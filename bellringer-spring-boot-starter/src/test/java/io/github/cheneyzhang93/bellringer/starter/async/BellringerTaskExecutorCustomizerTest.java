package io.github.cheneyzhang93.bellringer.starter.async;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/** F5 线程命名验收：Boot 缺省前缀改写为 {app}-async-，宿主显式前缀不动。 */
class BellringerTaskExecutorCustomizerTest {

    private final BellringerTaskExecutorCustomizer customizer =
            new BellringerTaskExecutorCustomizer(identity("payments-api"));

    @Test
    void rewritesBootDefaultPrefix() {
        // Boot 由 spring.task.execution.thread-name-prefix 缺省值 "task-" 触达定制器
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-");
        customizer.customize(executor);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("payments-api-async-");
    }

    @Test
    void rewritesBlankPrefix() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("   ");
        customizer.customize(executor);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("payments-api-async-");
    }

    @Test
    void keepsHostCustomPrefix() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("host-pool-");
        customizer.customize(executor);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("host-pool-");
    }

    @Test
    void keepsPrefixThatMerelyStartsWithBootDefault() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-scheduler-");
        customizer.customize(executor);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("task-scheduler-");
    }

    private static AppIdentity identity(String app) {
        BellringerProperties properties = new BellringerProperties();
        properties.setApp(app);
        return AppIdentity.resolve(properties, new MockEnvironment());
    }
}
