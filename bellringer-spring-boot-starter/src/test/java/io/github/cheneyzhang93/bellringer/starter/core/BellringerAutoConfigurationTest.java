package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.trace.MdcKeys;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 根装配验收（F10）：缺省零装配、开启后全量装配、F5 异常快速失败、SPI 让位、F8 键族绑定。
 */
class BellringerAutoConfigurationTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void importsFileRegistersAllAutoConfigurations() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(BellringerAutoConfiguration.class.getName(),
                        "io.github.cheneyzhang93.bellringer.starter.engine.PipelineAutoConfiguration",
                        "io.github.cheneyzhang93.bellringer.starter.engine.FallbackDedupAutoConfiguration",
                        "io.github.cheneyzhang93.bellringer.starter.outlet.SenderAutoConfiguration",
                        "io.github.cheneyzhang93.bellringer.starter.outlet.FallbackSenderAutoConfiguration",
                        "io.github.cheneyzhang93.bellringer.starter.report.ReportAutoConfiguration",
                        "io.github.cheneyzhang93.bellringer.starter.source.SourceAutoConfiguration");
    }

    @Test
    void disabledByDefaultAssemblesNothing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BellringerProperties.class);
            assertThat(context).doesNotHaveBean(AppIdentity.class);
            assertThat(context).doesNotHaveBean(TaskExecutorCustomizer.class);
            assertThat(context).doesNotHaveBean(TaskDecorator.class);
            assertThat(context).doesNotHaveBean(AlertSender.class);
        });
    }

    @Test
    void enabledAssemblesCoreBeans() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=payments-api",
                        "observability.env=prod")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppIdentity identity = context.getBean(AppIdentity.class);
                    assertThat(identity.getApp()).isEqualTo("payments-api");
                    assertThat(identity.getEnv()).isEqualTo("prod");
                    assertThat(context).hasSingleBean(TaskExecutorCustomizer.class);
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context).hasSingleBean(AlertEventFactory.class);
                    // 出口 SPI 兜底与管道属 S4 装配类，根配置刻意不产出任何 AlertSender
                    assertThat(context).doesNotHaveBean(AlertSender.class);
                });
    }

    @Test
    void enabledWithoutExplicitAppFallsBackToSpringApplicationName() {
        runner.withPropertyValues("observability.enabled=true", "spring.application.name=order-center")
                .run(context -> assertThat(context.getBean(AppIdentity.class).getApp()).isEqualTo("order-center"));
    }

    @Test
    void failsFastWhenAppIdentityMissing() {
        runner.withPropertyValues("observability.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("observability.app");
        });
    }

    @Test
    void hostTaskDecoratorBeanKeepsBellringerOneOut() {
        runner.withUserConfiguration(CustomDecoratorConfiguration.class)
                .withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context.getBean(TaskDecorator.class))
                            .isSameAs(context.getBean("hostTaskDecorator", TaskDecorator.class));
                });
    }

    @Test
    void defaultsMatchFrozenContract() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    BellringerProperties p = context.getBean(BellringerProperties.class);
                    assertThat(p.isEnabled()).isTrue();
                    assertThat(p.getReport().getMode()).isEqualTo(BellringerProperties.Report.Mode.WEBHOOK);
                    assertThat(p.getReport().getQueueSize()).isEqualTo(1024);
                    assertThat(p.getReport().getTimeoutMillis()).isEqualTo(3000L);
                    assertThat(p.getReport().getMaxRetries()).isEqualTo(5);
                    assertThat(p.getReport().getHeartbeatIntervalSeconds()).isEqualTo(30);
                    assertThat(p.getDedup().isEnabled()).isTrue();
                    assertThat(p.getDedup().getWindowSeconds()).isEqualTo(300);
                    assertThat(p.getDedup().getKeyPrefix()).isEqualTo("obs:alert:dedup");
                    assertThat(p.getAlert().getDingtalk().isEnabled()).isFalse();
                    assertThat(p.getSlowSql().isEnabled()).isTrue();
                    assertThat(p.getSlowSql().getMillis()).isEqualTo(500L);
                    assertThat(p.getSlowSql().getUpgradeMillis()).isEqualTo(3000L);
                });
    }

    @Test
    void kebabCasePropertyBinding() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.report.mode=both",
                        "observability.report.max-retries=7",
                        "observability.report.timeout-millis=1500",
                        "observability.report.queue-size=64",
                        "observability.dedup.window-seconds=60",
                        "observability.slow-sql.millis=800",
                        "observability.slow-sql.upgrade-millis=4000")
                .run(context -> {
                    BellringerProperties p = context.getBean(BellringerProperties.class);
                    assertThat(p.getReport().getMode()).isEqualTo(BellringerProperties.Report.Mode.BOTH);
                    assertThat(p.getReport().getMaxRetries()).isEqualTo(7);
                    assertThat(p.getReport().getTimeoutMillis()).isEqualTo(1500L);
                    assertThat(p.getReport().getQueueSize()).isEqualTo(64);
                    assertThat(p.getDedup().getWindowSeconds()).isEqualTo(60);
                    assertThat(p.getSlowSql().getMillis()).isEqualTo(800L);
                    assertThat(p.getSlowSql().getUpgradeMillis()).isEqualTo(4000L);
                });
    }

    @Test
    void bootManagedExecutorGetsAppPrefixAndMdcPropagation() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                        TaskExecutionAutoConfiguration.class))
                .withPropertyValues("observability.enabled=true", "observability.app=payments-api")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThreadPoolTaskExecutor executor = context.getBean(
                            TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
                            ThreadPoolTaskExecutor.class);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("payments-api-async-");

                    MDC.put(MdcKeys.TRACE_ID, TRACE);
                    Callable<String> probe = () -> MDC.get(MdcKeys.TRACE_ID);
                    assertThat(executor.submit(probe).get(10, TimeUnit.SECONDS)).isEqualTo(TRACE);

                    MDC.clear();
                    assertThat(executor.submit(probe).get(10, TimeUnit.SECONDS)).isNull();
                });
    }

    @Test
    void hostExecutorPrefixIsRespected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                        TaskExecutionAutoConfiguration.class))
                .withPropertyValues("observability.enabled=true", "observability.app=payments-api",
                        "spring.task.execution.thread-name-prefix=host-pool-")
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean(
                            TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
                            ThreadPoolTaskExecutor.class);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("host-pool-");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoratorConfiguration {

        @Bean
        TaskDecorator hostTaskDecorator() {
            return runnable -> runnable;
        }
    }
}
