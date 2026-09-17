package io.github.cheneyzhang93.bellringer.starter.engine;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 窗口去重（F6 键结构 {@code {prefix}:{app}:{type}:{aggregateKey}}，SETNX + TTL）。
 *
 * <p>显式启用（{@code observability.dedup.redis=true}，需宿主自带 spring-data-redis）——
 * 用于多实例部署时跨实例去重；Redis 故障一律 fail-open 放行（宁可重复不可漏发）。
 */
public class RedisDeduplicator implements Deduplicator {

    private static final Logger log = LoggerFactory.getLogger(RedisDeduplicator.class);

    private final StringRedisTemplate redisTemplate;

    private final String keyPrefix;

    private final int windowSeconds;

    public RedisDeduplicator(StringRedisTemplate redisTemplate, String keyPrefix, int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean firstWithinWindow(String app, String type, String aggregateKey) {
        String key = keyPrefix + ":" + app + ":" + type + ":" + aggregateKey;
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(windowSeconds));
            return first == null || first.booleanValue();
        } catch (RuntimeException e) {
            log.warn("[去重] Redis 异常，本事件放行（fail-open）key={}: {}", key, e.toString());
            return true;
        }
    }
}
