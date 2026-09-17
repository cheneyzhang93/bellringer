package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 管道装配验收：缺省内存去重、显式 Redis 去重（缺依赖/缺模板快速失败）、宿主覆盖。
 */
class PipelineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                    PipelineAutoConfiguration.class, FallbackDedupAutoConfiguration.class));

    @Test
    void assemblesPipelineWithInMemoryDedupAndPrefixedExecutor() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AlertPipeline.class);
                    assertThat(context).hasSingleBean(Deduplicator.class);
                    assertThat(context.getBean(Deduplicator.class)).isInstanceOf(InMemoryDeduplicator.class);
                    ThreadPoolTaskExecutor executor = context.getBean("bellringerAlertExecutor",
                            ThreadPoolTaskExecutor.class);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("demo-alert-");
                });
    }

    @Test
    void redisOptInWithoutTemplateFailsFast() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.dedup.redis=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("StringRedisTemplate");
                });
    }

    @Test
    void redisOptInWithTemplateAssemblesRedisDeduplicator() {
        runner.withUserConfiguration(StubRedisConfiguration.class)
                .withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.dedup.redis=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Deduplicator.class);
                    assertThat(context.getBean(Deduplicator.class)).isInstanceOf(RedisDeduplicator.class);
                });
    }

    @Test
    void hostDeduplicatorBeanWinsOverBothDefaults() {
        runner.withUserConfiguration(HostDeduplicatorConfiguration.class)
                .withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.dedup.redis=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Deduplicator.class);
                    assertThat(context.getBean(Deduplicator.class))
                            .isSameAs(context.getBean("hostDeduplicator", Deduplicator.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class StubRedisConfiguration {

        @Bean
        StringRedisTemplate stubRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostDeduplicatorConfiguration {

        @Bean
        Deduplicator hostDeduplicator() {
            return new InMemoryDeduplicator(60);
        }
    }
}
