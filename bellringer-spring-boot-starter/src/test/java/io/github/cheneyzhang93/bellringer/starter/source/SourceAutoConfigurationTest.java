package io.github.cheneyzhang93.bellringer.starter.source;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.engine.FallbackDedupAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.engine.PipelineAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.source.access.AccessLogFilter;
import io.github.cheneyzhang93.bellringer.starter.source.errorlog.ErrorLogAppender;
import io.github.cheneyzhang93.bellringer.starter.source.errorlog.ErrorLogEventSource;
import io.github.cheneyzhang93.bellringer.starter.source.health.HealthCheckTask;
import io.github.cheneyzhang93.bellringer.starter.source.schedule.ScheduledTaskErrorHandler;
import io.github.cheneyzhang93.bellringer.starter.source.sql.LockTimeoutInterceptor;
import io.github.cheneyzhang93.bellringer.starter.source.sql.SlowSqlInterceptor;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 事件源条件装配验收：缺省全量装配、逐源开关、目标驱动（健康自检）、宿主覆盖、总开关零装配。
 */
class SourceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                    FallbackDedupAutoConfiguration.class, PipelineAutoConfiguration.class,
                    SourceAutoConfiguration.class))
            .withPropertyValues("observability.enabled=true", "observability.app=demo");

    @Test
    void defaultsAssembleAllApplicableSources() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AlertEventFactory.class);
            assertThat(context).hasSingleBean(ErrorLogAppender.class);
            assertThat(context).hasSingleBean(ErrorLogEventSource.class);
            assertThat(context).hasSingleBean(SlowSqlInterceptor.class);
            assertThat(context).hasSingleBean(LockTimeoutInterceptor.class);
            assertThat(context).hasSingleBean(ScheduledTaskErrorHandler.class);
            assertThat(context).hasSingleBean(TaskSchedulerCustomizer.class);
            // 健康自检无目标、访问日志默认关闭：都不装配
            assertThat(context).doesNotHaveBean(HealthCheckTask.class);
            assertThat(context).doesNotHaveBean(AccessLogFilter.class);
        });
    }

    @Test
    void accessLogAssemblesOnlyWhenEnabled() {
        runner.withPropertyValues("observability.sources.access-log.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AccessLogFilter.class);
                    assertThat(context.getBean(AccessLogFilter.class).getOrder()).isEqualTo(AccessLogFilter.ORDER);
                });
    }

    @Test
    void healthCheckAssemblesWhenTargetsConfigured() {
        runner.withPropertyValues("observability.sources.health-check.targets[0].name=probe",
                        "observability.sources.health-check.targets[0].url=http://127.0.0.1:1/health",
                        "observability.sources.health-check.targets[0].timeout-millis=200",
                        "observability.sources.health-check.initial-delay-seconds=3600")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HealthCheckTask.class);
                    assertThat(context).hasSingleBean(BellringerProperties.class);
                });
    }

    @Test
    void healthCheckSkippedWhenEnabledWithoutTargets() {
        runner.withPropertyValues("observability.sources.health-check.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HealthCheckTask.class);
                });
    }

    @Test
    void eachSourceCanBeDisabledIndividually() {
        runner.withPropertyValues("observability.sources.error-log.enabled=false",
                        "observability.slow-sql.enabled=false",
                        "observability.sources.lock-timeout.enabled=false",
                        "observability.sources.scheduled-task.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ErrorLogAppender.class);
                    assertThat(context).doesNotHaveBean(ErrorLogEventSource.class);
                    assertThat(context).doesNotHaveBean(SlowSqlInterceptor.class);
                    assertThat(context).doesNotHaveBean(LockTimeoutInterceptor.class);
                    assertThat(context).doesNotHaveBean(ScheduledTaskErrorHandler.class);
                    assertThat(context).doesNotHaveBean(TaskSchedulerCustomizer.class);
                });
    }

    @Test
    void masterSwitchOffAssemblesNoSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SourceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ErrorLogAppender.class);
                    assertThat(context).doesNotHaveBean(SlowSqlInterceptor.class);
                    assertThat(context).doesNotHaveBean(AccessLogFilter.class);
                });
    }

    @Test
    void hostBeansReplaceDefaults() {
        runner.withUserConfiguration(HostSourceConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(SlowSqlInterceptor.class);
            assertThat(context.getBean(SlowSqlInterceptor.class))
                    .isSameAs(context.getBean("hostSlowSqlInterceptor", SlowSqlInterceptor.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class HostSourceConfiguration {

        @Bean
        SlowSqlInterceptor hostSlowSqlInterceptor() {
            return new SlowSqlInterceptor(new BellringerProperties(),
                    new AlertEventFactory(TestIdentities.app("demo")), event -> {
            });
        }
    }
}
