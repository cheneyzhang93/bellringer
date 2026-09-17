package io.github.cheneyzhang93.bellringer.starter.outlet;

import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.DingTalkWebhookSender;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.FeishuWebhookSender;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.WeComWebhookSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 群机器人出口装配（OSS 通道族，F7：多通道并存 = 多个 {@link AlertSender} Bean，管道复合收集全量发送）。
 *
 * <p>每个通道：enabled=true 且 webhook 已配置才装配；enabled=true 但 webhook 为空＝显式配置错误，
 * 启动即失败（宁可启动报错，不要静默丢告警）。宿主可用同类型 Bean 覆盖内置实现。
 */
@AutoConfiguration(after = BellringerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class SenderAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "observability.alert.dingtalk", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(DingTalkWebhookSender.class)
    public AlertSender bellringerDingTalkSender(BellringerProperties properties) {
        BellringerProperties.Alert.DingTalk config = properties.getAlert().getDingtalk();
        if (isBlank(config.getWebhook())) {
            throw new IllegalStateException(
                    "敲钟人：observability.alert.dingtalk.enabled=true 但 webhook 未配置（请填写群机器人 webhook 地址）");
        }
        return new DingTalkWebhookSender(config,
                properties.getAlert().getConnectTimeoutMillis(), properties.getAlert().getReadTimeoutMillis());
    }

    @Bean
    @ConditionalOnProperty(prefix = "observability.alert.wecom", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(WeComWebhookSender.class)
    public AlertSender bellringerWeComSender(BellringerProperties properties) {
        BellringerProperties.Alert.WeCom config = properties.getAlert().getWecom();
        if (isBlank(config.getWebhook())) {
            throw new IllegalStateException(
                    "敲钟人：observability.alert.wecom.enabled=true 但 webhook 未配置（请填写群机器人 webhook 地址）");
        }
        return new WeComWebhookSender(config,
                properties.getAlert().getConnectTimeoutMillis(), properties.getAlert().getReadTimeoutMillis());
    }

    @Bean
    @ConditionalOnProperty(prefix = "observability.alert.feishu", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(FeishuWebhookSender.class)
    public AlertSender bellringerFeishuSender(BellringerProperties properties) {
        BellringerProperties.Alert.Feishu config = properties.getAlert().getFeishu();
        if (isBlank(config.getWebhook())) {
            throw new IllegalStateException(
                    "敲钟人：observability.alert.feishu.enabled=true 但 webhook 未配置（请填写群机器人 webhook 地址）");
        }
        return new FeishuWebhookSender(config,
                properties.getAlert().getConnectTimeoutMillis(), properties.getAlert().getReadTimeoutMillis());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
