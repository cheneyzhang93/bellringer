package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ApplicationStartedAt;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.core.ReportOutlet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 事件管道装配（F6/F7）：告警线程池、去重（Redis 显式启用，缺省由 {@link FallbackDedupAutoConfiguration}
 * 提供内存实现）、markdown 渲染、{@link AlertPipeline} 本体。
 */
@AutoConfiguration(after = BellringerAutoConfiguration.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class PipelineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineAutoConfiguration.class);

    /** 推送专用线程池：队列满丢弃并留痕，告警绝不允许拖垮业务线程。 */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor bellringerAlertExecutor(AppIdentity identity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix(identity.getApp() + "-alert-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    /** 装配期定格启动时刻（F2 心跳 startedAt 语义：启动时刻而非发送时刻）。 */
    @Bean
    public ApplicationStartedAt bellringerApplicationStartedAt() {
        return new ApplicationStartedAt(System.currentTimeMillis());
    }

    @Bean
    @ConditionalOnMissingBean(MarkdownRenderer.class)
    public MarkdownRenderer bellringerMarkdownRenderer(BellringerProperties properties, AppIdentity identity) {
        return new MarkdownRenderer(properties, identity);
    }

    @Bean
    @ConditionalOnMissingBean(AlertPipeline.class)
    public AlertPipeline bellringerAlertPipeline(BellringerProperties properties,
                                                 List<AlertSender> senders,
                                                 Deduplicator deduplicator,
                                                 MarkdownRenderer renderer,
                                                 ThreadPoolTaskExecutor bellringerAlertExecutor,
                                                 ObjectProvider<ReportOutlet> reportOutlet) {
        return new AlertPipeline(properties, senders, deduplicator, renderer, bellringerAlertExecutor, reportOutlet);
    }

    /** 显式启用 Redis 去重（多实例场景）：dedup.redis=true 且容器内确有 StringRedisTemplate。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "observability.dedup", name = "redis", havingValue = "true")
    static class RedisDedupConfiguration {

        @Bean
        @ConditionalOnMissingBean(Deduplicator.class)
        public Deduplicator bellringerRedisDeduplicator(ObjectProvider<StringRedisTemplate> redisTemplate,
                                                        BellringerProperties properties) {
            StringRedisTemplate template = redisTemplate.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException("敲钟人：observability.dedup.redis=true 但容器内没有 StringRedisTemplate"
                        + "（请引入 spring-boot-starter-data-redis 并配置连接）");
            }
            log.info("[去重] Redis 窗口去重已启用 prefix={} window={}s",
                    properties.getDedup().getKeyPrefix(), properties.getDedup().getWindowSeconds());
            return new RedisDeduplicator(template, properties.getDedup().getKeyPrefix(),
                    properties.getDedup().getWindowSeconds());
        }
    }
}
