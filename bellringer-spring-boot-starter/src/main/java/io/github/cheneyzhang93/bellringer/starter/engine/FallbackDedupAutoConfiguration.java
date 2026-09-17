package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 内存去重兜底（F6 缺省路径：零基建部署无 Redis）。
 *
 * <p>独立装配类 + 排在 {@link PipelineAutoConfiguration} 之后，保证显式启用的 Redis 去重
 * （dedup.redis=true）优先注册、本兜底自动让位（{@code @ConditionalOnMissingBean} 的确定性排序）。
 */
@AutoConfiguration(after = PipelineAutoConfiguration.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class FallbackDedupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Deduplicator.class)
    public Deduplicator bellringerInMemoryDeduplicator(BellringerProperties properties) {
        return new InMemoryDeduplicator(properties.getDedup().getWindowSeconds());
    }
}
