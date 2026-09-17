package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.InstanceIds;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F5 取值链验收：app/env/instance 三元的解析优先级与 fail-fast。 */
class AppIdentityTest {

    private final BellringerProperties properties = new BellringerProperties();

    @Test
    void explicitAppWins() {
        properties.setApp("explicit-app");
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "spring-app");
        assertThat(AppIdentity.resolve(properties, environment).getApp()).isEqualTo("explicit-app");
    }

    @Test
    void fallsBackToSpringApplicationName() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "order-center");
        assertThat(AppIdentity.resolve(properties, environment).getApp()).isEqualTo("order-center");
    }

    @Test
    void blankAppIsTreatedAsMissing() {
        properties.setApp("   ");
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "order-center");
        assertThat(AppIdentity.resolve(properties, environment).getApp()).isEqualTo("order-center");
    }

    @Test
    void failsFastWhenBothMissing() {
        assertThatThrownBy(() -> AppIdentity.resolve(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observability.app")
                .hasMessageContaining("spring.application.name");
    }

    @Test
    void envPrefersConfiguredValueThenProfileThenDefault() {
        properties.setApp("demo");
        properties.setEnv("staging");
        MockEnvironment withProfile = new MockEnvironment();
        withProfile.setActiveProfiles("dev");
        assertThat(AppIdentity.resolve(properties, withProfile).getEnv()).isEqualTo("staging");

        properties.setEnv(null);
        assertThat(AppIdentity.resolve(properties, withProfile).getEnv()).isEqualTo("dev");
        assertThat(AppIdentity.resolve(properties, new MockEnvironment()).getEnv()).isEqualTo("default");
    }

    @Test
    void instanceMatchesProtocolPattern() {
        properties.setApp("demo");
        AppIdentity identity = AppIdentity.resolve(properties, new MockEnvironment());
        assertThat(identity.getInstance()).matches(InstanceIds.PATTERN);
    }
}
