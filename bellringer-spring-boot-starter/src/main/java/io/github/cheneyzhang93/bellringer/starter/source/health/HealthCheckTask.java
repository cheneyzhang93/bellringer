package io.github.cheneyzhang93.bellringer.starter.source.health;

import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 健康自检事件源：自有单线程守护调度器周期探测配置目标（HTTP GET，2xx/3xx 视为存活）。
 *
 * <p>抖动抑制：连续失败达阈值（{@code failure-threshold}）才判失联并发 P1；恢复时发一条 P2。
 * 状态迁移才发事件（失联期间不重复轰炸），聚合键区分方向（{@code down|目标} / {@code up|目标}）。
 * 目标清单为空＝不启动（零线程零网络），与"默认 enabled 但未配置"的宿主零冲突。
 */
public class HealthCheckTask implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckTask.class);

    private final BellringerProperties.Sources.HealthCheck config;

    private final AppIdentity identity;

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    private final ScheduledExecutorService executor;

    private final Map<String, TargetState> states = new ConcurrentHashMap<String, TargetState>();

    private volatile boolean running;

    public HealthCheckTask(BellringerProperties.Sources.HealthCheck config, AppIdentity identity,
                           AlertEventFactory events, AlertEventSink sink) {
        this.config = config;
        this.identity = identity;
        this.events = events;
        this.sink = sink;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, HealthCheckTask.this.identity.getApp() + "-health-1");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void start() {
        if (running || config.getTargets().isEmpty()) {
            return;
        }
        running = true;
        executor.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                probeAll();
            }
        }, config.getInitialDelaySeconds(), Math.max(1, config.getIntervalSeconds()), TimeUnit.SECONDS);
        log.info("[健康自检] 已启动 {} 个目标，周期 {}s，连续失败 {} 次判失联",
                config.getTargets().size(), config.getIntervalSeconds(), config.getFailureThreshold());
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void probeAll() {
        for (BellringerProperties.Sources.HealthCheck.Target target : config.getTargets()) {
            try {
                probe(target);
            } catch (Throwable t) {
                // 单目标探测异常不得终止巡检循环
                log.warn("[健康自检] 探测 {} 异常（已隔离）: {}", target.getUrl(), t.toString());
            }
        }
    }

    private void probe(BellringerProperties.Sources.HealthCheck.Target target) {
        String key = keyOf(target);
        TargetState state = states.computeIfAbsent(key, k -> new TargetState());
        ProbeResult result = ping(target);
        synchronized (state) {
            if (result.alive) {
                state.consecutiveFailures = 0;
                if (state.down) {
                    state.down = false;
                    emitRecovered(target, key, result);
                }
            } else {
                state.consecutiveFailures++;
                if (!state.down && state.consecutiveFailures >= Math.max(1, config.getFailureThreshold())) {
                    state.down = true;
                    emitDown(target, key, result, state.consecutiveFailures);
                }
            }
        }
    }

    private ProbeResult ping(BellringerProperties.Sources.HealthCheck.Target target) {
        long startNanos = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(target.getUrl()).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(target.getTimeoutMillis());
            connection.setReadTimeout(target.getTimeoutMillis());
            connection.setInstanceFollowRedirects(false); // 3xx 本身即存活，不跟着跳
            int status = connection.getResponseCode();
            boolean alive = status >= 200 && status < 400;
            return new ProbeResult(alive, status, millisSince(startNanos),
                    alive ? null : "HTTP " + status);
        } catch (Exception e) {
            return new ProbeResult(false, -1, millisSince(startNanos), e.toString());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void emitDown(BellringerProperties.Sources.HealthCheck.Target target, String key,
                          ProbeResult result, int failures) {
        sink.emit(events.builder(EventTypes.HEALTH_CHECK)
                .level(AlertLevel.P1)
                .title("健康自检失败：" + key)
                .message("目标连续 " + failures + " 次探测失败，判为不可用\n"
                        + "目标：" + key + "\n"
                        + "地址：" + target.getUrl() + "\n"
                        + "最近结果：" + result.describe() + "\n"
                        + "耗时：" + result.millis + "ms")
                .source(target.getUrl())
                .aggregateKey("down|" + key)
                .build());
    }

    private void emitRecovered(BellringerProperties.Sources.HealthCheck.Target target, String key,
                               ProbeResult result) {
        sink.emit(events.builder(EventTypes.HEALTH_CHECK)
                .level(AlertLevel.P2)
                .title("健康自检恢复：" + key)
                .message("目标已恢复响应\n"
                        + "目标：" + key + "\n"
                        + "地址：" + target.getUrl() + "\n"
                        + "当前结果：" + result.describe() + "\n"
                        + "耗时：" + result.millis + "ms")
                .source(target.getUrl())
                .aggregateKey("up|" + key)
                .build());
    }

    private static String keyOf(BellringerProperties.Sources.HealthCheck.Target target) {
        String name = target.getName();
        return name == null || name.trim().isEmpty() ? target.getUrl() : name;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 单目标探测状态（仅巡检线程读写，同步块保护便于测试注入）。 */
    private static final class TargetState {

        private int consecutiveFailures;

        private boolean down;
    }

    /** 单次探测结果。 */
    private static final class ProbeResult {

        private final boolean alive;

        private final int status;

        private final long millis;

        private final String error;

        ProbeResult(boolean alive, int status, long millis, String error) {
            this.alive = alive;
            this.status = status;
            this.millis = millis;
            this.error = error;
        }

        String describe() {
            if (status > 0) {
                return "HTTP " + status;
            }
            return error == null ? "无响应" : error;
        }
    }
}
