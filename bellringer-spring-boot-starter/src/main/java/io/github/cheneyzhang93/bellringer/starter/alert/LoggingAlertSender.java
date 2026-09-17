package io.github.cheneyzhang93.bellringer.starter.alert;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志兜底通道：未配置任何外呼通道时，把告警以 ERROR 落日志（联调/内网可见，不触网）。
 *
 * <p>仅在容器内无其他 {@link AlertSender} Bean 时装配（{@code @ConditionalOnMissingBean}）。
 * ERROR 出口采集时排除本包 logger，防止"兜底日志 → 事件 → 兜底日志"回环。
 */
public class LoggingAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertSender.class);

    public static final String NAME = "logging";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void send(AlertEvent event, String markdown) {
        log.error("[敲钟人] 告警输出（未配置外呼通道，落日志）type={} level={} title={}\n{}",
                event.getType(), event.getLevel(), event.getTitle(), markdown);
    }
}
