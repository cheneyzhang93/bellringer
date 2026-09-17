package io.github.cheneyzhang93.bellringer.starter.core;

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
 *       （F10.3，与宿主异步配置零冲突）。</li>
 * </ul>
 *
 * <p>事件源（S3）与出口通道（S4）各自独立自动装配类，经 {@code @AutoConfiguration(after/before)} 编排；
 * 出口兜底（日志）排在真实通道之后，见 {@code FallbackSenderAutoConfiguration}。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BellringerProperties.class)
public class BellringerAutoConfiguration {

    @Bean
    public AppIdentity bellringerAppIdentity(BellringerProperties properties, Environment environment) {
        return AppIdentity.resolve(properties, environment);
    }

    /** 事件工厂：宿主与 S3 事件源共用的埋点入口（预填 F5 身份口径）。 */
    @Bean
    @ConditionalOnMissingBean
    public AlertEventFactory bellringerAlertEventFactory(AppIdentity bellringerAppIdentity) {
        return new AlertEventFactory(bellringerAppIdentity);
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
}
