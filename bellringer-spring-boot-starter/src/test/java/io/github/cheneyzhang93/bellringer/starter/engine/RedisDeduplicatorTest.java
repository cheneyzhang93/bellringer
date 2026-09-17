package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.testing.TestEvents;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 去重验收：F6 键结构、SETNX 结果映射、故障 fail-open 放行。
 */
class RedisDeduplicatorTest {

    private StringRedisTemplate template;

    private ValueOperations<String, String> valueOps;

    private RedisDeduplicator deduplicator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);
        deduplicator = new RedisDeduplicator(template, "obs:alert:dedup", 300);
    }

    @Test
    void usesFrozenKeyStructureAndTtl() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        assertThat(deduplicator.firstWithinWindow("payments-api", "system-error", "Order#pay")).isTrue();

        verify(valueOps).setIfAbsent(eq("obs:alert:dedup:payments-api:system-error:Order#pay"),
                eq("1"), eq(Duration.ofSeconds(300)));
    }

    @Test
    void setIfAbsentTruePassesFalseSuppresses() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE, Boolean.FALSE);

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key")).isTrue();
        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key")).isFalse();
    }

    @Test
    void nullResultIsTreatedAsFirst() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key")).isTrue();
    }

    @Test
    void redisFailureFailsOpen() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key")).isTrue();
    }

    @Test
    void windowSecondsControlTtl() {
        RedisDeduplicator shortWindow = new RedisDeduplicator(template, "custom:prefix", 60);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        shortWindow.firstWithinWindow("app", "slow-sql", "fp");

        verify(valueOps).setIfAbsent(eq("custom:prefix:app:slow-sql:fp"), eq("1"), eq(Duration.ofSeconds(60)));
    }

    /** 便于编译器确认测试依赖的配置对象可构造（保持与生产一致的取值链）。 */
    @Test
    void appIdentityStillResolvable() {
        BellringerProperties properties = new BellringerProperties();
        properties.setApp("demo");
        AppIdentity identity = AppIdentity.resolve(properties, new MockEnvironment());
        assertThat(identity.getApp()).isEqualTo("demo");
        AlertEvent event = TestEvents.sample("demo").build();
        assertThat(event.getApp()).isEqualTo("demo");
    }
}
