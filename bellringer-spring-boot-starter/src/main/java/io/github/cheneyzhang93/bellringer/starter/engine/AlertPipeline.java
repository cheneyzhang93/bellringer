package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.core.LocalAlertPusher;
import io.github.cheneyzhang93.bellringer.starter.core.ReportOutlet;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 事件管道（F6 双出口分离）：
 * <pre>
 *   emit(event)
 *     ├─ 上报出口：全量不删（HttpEventReporter 有界队列，未装配时跳过）
 *     └─ 推送出口（异步）：去重 → markdown 渲染 → AlertSender 链（全部发送器）
 * </pre>
 *
 * <p>推送出口异步 + 有界队列：业务线程永不阻塞；推送失败单次尝试不重试（F7，重试语义只属于上报出口）。
 * {@link #pushLocal(AlertEvent)} 只走推送出口，供上报通道自身故障（401）时本地告警，杜绝回环。
 */
public class AlertPipeline implements AlertEventSink, LocalAlertPusher {

    private static final Logger log = LoggerFactory.getLogger(AlertPipeline.class);

    private final BellringerProperties properties;

    private final List<AlertSender> senders;

    private final Deduplicator deduplicator;

    private final MarkdownRenderer renderer;

    private final ThreadPoolTaskExecutor executor;

    private final ObjectProvider<ReportOutlet> reportOutlet;

    public AlertPipeline(BellringerProperties properties,
                         List<AlertSender> senders,
                         Deduplicator deduplicator,
                         MarkdownRenderer renderer,
                         ThreadPoolTaskExecutor executor,
                         ObjectProvider<ReportOutlet> reportOutlet) {
        this.properties = properties;
        this.senders = senders;
        this.deduplicator = deduplicator;
        this.renderer = renderer;
        this.executor = executor;
        this.reportOutlet = reportOutlet;
    }

    @Override
    public void emit(AlertEvent event) {
        if (event == null) {
            return;
        }
        List<String> violations = event.validate();
        if (!violations.isEmpty()) {
            log.warn("[事件总线] 事件不合 F4 契约，丢弃 type={} violations={}", event.getType(), violations);
            return;
        }
        ReportOutlet outlet = reportOutlet.getIfAvailable();
        if (outlet != null) {
            outlet.enqueue(event);
        }
        dispatch(event);
    }

    @Override
    public void pushLocal(AlertEvent event) {
        if (event != null) {
            dispatch(event);
        }
    }

    private void dispatch(AlertEvent event) {
        try {
            executor.execute(() -> push(event));
        } catch (RejectedExecutionException e) {
            // 推送队列满：告警可丢但必须留痕，绝不向上抛（业务线程保护）
            log.error("[推送出口] 队列已满，事件丢弃 type={} title={}", event.getType(), event.getTitle());
        }
    }

    private void push(AlertEvent event) {
        try {
            String aggregateKey = event.getAggregateKey();
            boolean dedupable = properties.getDedup().isEnabled() && aggregateKey != null && !aggregateKey.isEmpty();
            if (dedupable && !deduplicator.firstWithinWindow(event.getApp(), event.getType(), aggregateKey)) {
                log.debug("[推送出口] 窗口内重复丢弃 type={} aggregateKey={}", event.getType(), aggregateKey);
                return;
            }
            String markdown = renderer.render(event);
            for (AlertSender sender : senders) {
                try {
                    sender.send(event, markdown);
                } catch (Exception e) {
                    log.error("[推送出口] 发送失败（单次尝试不重试）sender={} type={} title={}: {}",
                            sender.name(), event.getType(), event.getTitle(), e.toString());
                }
            }
        } catch (Throwable t) {
            log.error("[推送出口] 管道异常（已隔离，不影响业务）type={}", event.getType(), t);
        }
    }
}
