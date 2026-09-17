package io.github.cheneyzhang93.bellringer.starter.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.ApiPaths;
import io.github.cheneyzhang93.bellringer.protocol.EventEnvelope;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ReportOutlet;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * 全量事件上报器（F1 客户端硬约束）：业务线程只做有界队列入队，独立守护线程发送。
 *
 * <ul>
 *   <li>有界队列：满则丢弃并计数（绝不阻塞业务线程）；</li>
 *   <li>指数退避重试 ≤ {@code observability.report.max-retries} 次（初始 1s、倍增、上限 30s）；</li>
 *   <li>单次超时 3s（{@code report.timeout-millis}）；429/5xx 退避重试，其余 4xx 放弃；</li>
 *   <li>401 由 {@link ReportClient} 统一处理：停止上报 + 本地告警一次。</li>
 * </ul>
 */
public class HttpEventReporter implements ReportOutlet, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HttpEventReporter.class);

    private static final long BACKOFF_INITIAL_MILLIS = 1000L;

    private static final long BACKOFF_CAP_MILLIS = 30_000L;

    private final BlockingQueue<AlertEvent> queue;

    private final ReportClient client;

    private final ReportState state;

    private final ReportSettings settings;

    private final Thread worker;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong dropped = new AtomicLong();

    public HttpEventReporter(ReportSettings settings, ReportClient client, ReportState state, AppIdentity identity) {
        this.settings = settings;
        this.client = client;
        this.state = state;
        this.queue = new ArrayBlockingQueue<AlertEvent>(settings.getQueueSize());
        this.worker = new Thread(this::loop, identity.getApp() + "-reporter-");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void enqueue(AlertEvent event) {
        if (event == null || state.isStopped()) {
            return;
        }
        if (!queue.offer(event)) {
            long total = dropped.incrementAndGet();
            if (total == 1 || total % 100 == 0) {
                log.warn("[上报出口] 队列已满，累计丢弃 {} 条（队列容量 {}；控制台断连属预期降级）", total, settings.getQueueSize());
            }
        }
    }

    /** 测试可见：累计丢弃条数。 */
    long getDroppedCount() {
        return dropped.get();
    }

    /** 测试可见：队列积压深度。 */
    int getQueueDepth() {
        return queue.size();
    }

    @Override
    public void destroy() {
        running.set(false);
        worker.interrupt();
    }

    private void loop() {
        while (running.get()) {
            try {
                AlertEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    deliver(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                log.error("[上报出口] 工作线程异常（已隔离，继续运行）", t);
            }
        }
    }

    private void deliver(AlertEvent event) {
        String json;
        try {
            json = ReportJson.MAPPER.writeValueAsString(EventEnvelope.of(SdkVersion.current(), event));
        } catch (JsonProcessingException e) {
            log.error("[上报出口] 序列化失败，丢弃 eventId={}: {}", event.getEventId(), e.toString());
            return;
        }
        int maxAttempts = settings.getMaxRetries() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!running.get()) {
                return;
            }
            try {
                int status = client.post(ApiPaths.EVENTS, json);
                if (status == ReportClient.NOT_SENT || (status >= 200 && status < 300)) {
                    return; // 已接收（202）或通道已停止
                }
                if (status == 401) {
                    return; // 停止 + 本地告警已由 ReportClient 完成
                }
                if (status == 429 || status >= 500) {
                    if (attempt == maxAttempts) {
                        log.error("[上报出口] 退避重试达上限，丢弃 eventId={} status={}", event.getEventId(), status);
                        return;
                    }
                    if (!sleepBackoff(attempt)) {
                        return;
                    }
                    continue;
                }
                log.error("[上报出口] 服务端拒绝（不重试）status={} eventId={}", status, event.getEventId());
                return;
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    log.error("[上报出口] 网络失败达重试上限，丢弃 eventId={}: {}", event.getEventId(), e.toString());
                    return;
                }
                if (!sleepBackoff(attempt)) {
                    return;
                }
            }
        }
    }

    /** 指数退避：1s、2s、4s、8s、16s…封顶 30s；返回 false＝线程被中断需退出。 */
    private boolean sleepBackoff(int attempt) {
        long delay = Math.min(BACKOFF_INITIAL_MILLIS << (attempt - 1), BACKOFF_CAP_MILLIS);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
