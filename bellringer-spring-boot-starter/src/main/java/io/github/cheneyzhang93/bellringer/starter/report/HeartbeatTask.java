package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.protocol.ApiPaths;
import io.github.cheneyzhang93.bellringer.protocol.HeartbeatEnvelope;
import io.github.cheneyzhang93.bellringer.protocol.InstanceHeartbeat;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ApplicationStartedAt;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.env.Environment;

/**
 * 实例心跳（F2）：默认 30s 一拍，控制台 90s（3 拍）判失联。
 *
 * <p>{@code startedAt} 为启动时刻（配合 instance 区分重启）；失败静默丢弃、下一拍补报；
 * 401 与事件上报共享 {@link ReportState}（同 Token），停止后不再心跳。
 */
public class HeartbeatTask implements DisposableBean {

    /** 首拍延迟：尽快让控制台看见新实例，同时避开启动高峰。 */
    static final int INITIAL_DELAY_SECONDS = 3;

    private static final Logger log = LoggerFactory.getLogger(HeartbeatTask.class);

    private final ScheduledExecutorService scheduler;

    private final ReportClient client;

    private final ReportState state;

    private final AppIdentity identity;

    private final ApplicationStartedAt startedAt;

    private final String appVersion;

    private final Map<String, Boolean> modules;

    public HeartbeatTask(ReportSettings settings, ReportClient client, ReportState state, AppIdentity identity,
                         ApplicationStartedAt startedAt, BellringerProperties properties, Environment environment) {
        this.client = client;
        this.state = state;
        this.identity = identity;
        this.startedAt = startedAt;
        this.appVersion = firstNonBlank(properties.getVersion(), environment.getProperty("spring.application.version"));
        this.modules = ModuleFlags.of(properties);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(
                identity.getApp() + "-heartbeat-"));
        this.scheduler.scheduleAtFixedRate(this::beat, INITIAL_DELAY_SECONDS,
                settings.getHeartbeatIntervalSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    private void beat() {
        if (state.isStopped()) {
            return;
        }
        try {
            InstanceHeartbeat heartbeat = InstanceHeartbeat.builder()
                    .app(identity.getApp())
                    .instance(identity.getInstance())
                    .version(appVersion)
                    .env(identity.getEnv())
                    .startedAt(startedAt.getEpochMillis())
                    .modules(modules)
                    .build();
            String json = ReportJson.MAPPER.writeValueAsString(HeartbeatEnvelope.of(SdkVersion.current(), heartbeat));
            int status = client.post(ApiPaths.INSTANCE_HEARTBEAT, json);
            if (status != ReportClient.NOT_SENT && !(status >= 200 && status < 300)) {
                log.debug("[心跳] 非 2xx（静默丢弃，下一拍补报）status={}", status);
            }
        } catch (Throwable t) {
            // 必须吞掉一切异常：定时任务抛出会取消后续调度，心跳不能因单拍失败永久停摆
            log.debug("[心跳] 发送失败（静默丢弃，下一拍补报）: {}", t.toString());
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        private final String name;

        DaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
