package io.github.cheneyzhang93.bellringer.starter.outlet;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.alert.LoggingAlertSender;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.DingTalkWebhookSender;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.FeishuWebhookSender;
import io.github.cheneyzhang93.bellringer.starter.outlet.webhook.WeComWebhookSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出口装配验收（F7）：多通道并存、enabled 无 webhook 快速失败、日志兜底让位。
 */
class SenderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                    SenderAutoConfiguration.class, FallbackSenderAutoConfiguration.class));

    @Test
    void allChannelsDisabledFallsBackToLoggingSender() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AlertSender.class);
                    assertThat(context.getBean(AlertSender.class)).isInstanceOf(LoggingAlertSender.class);
                });
    }

    @Test
    void dingtalkEnabledWithoutWebhookFailsFast() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.alert.dingtalk.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("webhook");
                });
    }

    @Test
    void multipleChannelsCoexistAndSuppressLoggingFallback() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.alert.dingtalk.enabled=true",
                        "observability.alert.dingtalk.webhook=https://oapi.dingtalk.com/robot/send?access_token=t",
                        "observability.alert.wecom.enabled=true",
                        "observability.alert.wecom.webhook=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=k",
                        "observability.alert.feishu.enabled=true",
                        "observability.alert.feishu.webhook=https://open.feishu.cn/open-apis/bot/v2/hook/h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DingTalkWebhookSender.class);
                    assertThat(context).hasSingleBean(WeComWebhookSender.class);
                    assertThat(context).hasSingleBean(FeishuWebhookSender.class);
                    List<String> names = new ArrayList<String>();
                    for (AlertSender sender : context.getBeansOfType(AlertSender.class).values()) {
                        names.add(sender.name());
                    }
                    Collections.sort(names);
                    assertThat(names).containsExactly("dingtalk", "feishu", "wecom");
                });
    }

    @Test
    void hostAlertSenderSuppressesLoggingFallback() {
        runner.withUserConfiguration(CustomSenderConfiguration.class)
                .withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlertSender.class);
                    assertThat(context.getBean(AlertSender.class).name()).isEqualTo("custom");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSenderConfiguration {

        @Bean
        AlertSender customAlertSender() {
            return new AlertSender() {
                @Override
                public String name() {
                    return "custom";
                }

                @Override
                public void send(AlertEvent event, String markdown) {
                    // 测试替身：不触网
                }
            };
        }
    }
}
