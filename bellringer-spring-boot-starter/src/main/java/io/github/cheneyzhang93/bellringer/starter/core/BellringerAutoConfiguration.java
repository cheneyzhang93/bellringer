package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.alert.LoggingAlertSender;
import io.github.cheneyzhang93.bellringer.starter.async.BellringerTaskDecorator;
import io.github.cheneyzhang93.bellringer.starter.async.BellringerTaskExecutorCustomizer;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;

/**
 * 敲钟人 starter 根自动装配（S2 骨架）。
 *
 * <p>装配约定（F10 全量条件装配验收）：
 * <ul>
 *   <li>唯一入口 {@code observability.enabled=true} 才装配；缺省/关闭＝零 Bean、零线程、零网络；</li>
 *   <li>{@link AppIdentity}：F5 取值链（observability.app → spring.application.name → 启动失败）；</li>
 *   <li>异步贯通（F5）：{@link TaskExecutorCustomizer} 增量改写线程名前缀 {app}-async-，
 *       {@link TaskDecorator} 传播 MDC——两者刻意不实现 {@code AsyncConfigurer}、不自建 Executor
 *       （F10.3，与宿主异步配置零冲突）；</li>
 *   <li>出口 SPI（F7）：宿主未提供任何 {@link AlertSender} 时装配日志兜底。</li>
 * </ul>
 *
 * <p>事件源（S3）与出口通道（S4）各自独立自动装配类，经 {@code @AutoConfiguration(after/before)} 编排。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BellringerProperties.class)
public class BellringerAutoConfiguration {

    @Bean
    public AppIdentity bellringerAppIdentity(BellringerProperties properties, Environment environment) {
        return AppIdentity.resolve(properties, environment);
    }

    @Bean
    public TaskExecutorCustomizer bellringerTaskExecutorCustomizer(AppIdentity bellringerAppIdentity) {
        return new BellringerTaskExecutorCustomizer(bellringerAppIdentity);
    }

    /** 宿主已自带 TaskDecorator 时让位：Boot 仅在唯一候选时应用，双份会互相抵消。 */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator bellringerTaskDecorator() {
        return new BellringerTaskDecorator();
    }

    /** 日志兜底出口：S4 拆至独立装配类并排在真实通道之后，本轮先保证 SPI 恒定可用。 */
    @Bean
    @ConditionalOnMissingBean(AlertSender.class)
    public AlertSender bellringerLoggingAlertSender() {
        return new LoggingAlertSender();
    }
}
