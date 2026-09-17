package io.github.cheneyzhang93.bellringer.starter.outlet;

import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.alert.LoggingAlertSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 日志兜底出口（F7 兜底位）：容器内无任何 {@link AlertSender} 时装配，告警落 ERROR 日志不触网。
 *
 * <p>必须排在通道装配类之后评估 {@code @ConditionalOnMissingBean}，否则会抢先占位导致真实通道永不装配。
 */
@AutoConfiguration(after = SenderAutoConfiguration.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class FallbackSenderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AlertSender.class)
    public AlertSender bellringerLoggingAlertSender() {
        return new LoggingAlertSender();
    }
}
