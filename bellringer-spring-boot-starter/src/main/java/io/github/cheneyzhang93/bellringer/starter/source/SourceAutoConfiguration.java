package io.github.cheneyzhang93.bellringer.starter.source;

import ch.qos.logback.classic.LoggerContext;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.engine.PipelineAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.source.access.AccessLogFilter;
import io.github.cheneyzhang93.bellringer.starter.source.errorlog.ErrorLogAppender;
import io.github.cheneyzhang93.bellringer.starter.source.errorlog.ErrorLogEventSource;
import io.github.cheneyzhang93.bellringer.starter.source.health.HealthCheckTask;
import io.github.cheneyzhang93.bellringer.starter.source.schedule.ScheduledTaskErrorCustomizer;
import io.github.cheneyzhang93.bellringer.starter.source.schedule.ScheduledTaskErrorHandler;
import io.github.cheneyzhang93.bellringer.starter.source.sql.LockTimeoutInterceptor;
import io.github.cheneyzhang93.bellringer.starter.source.sql.SlowSqlInterceptor;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * S3 事件源自动装配：ERROR 出口 / 慢 SQL / 锁超时 / 定时任务 / 健康自检 / 访问日志。
 *
 * <p>装配原则（F10 全量条件装配）：
 * <ul>
 *   <li>外层与根自动装配同门禁 {@code observability.enabled=true}；</li>
 *   <li>每个事件源独立条件：缺依赖（mybatis / logback / spring-web）或独立开关关闭＝该 Bean 不存在，
 *       不影响其它源——宿主不在用 MyBatis 也能开 ERROR 出口；</li>
 *   <li>所有 Bean 都 {@code @ConditionalOnMissingBean}，宿主可整体替换任一源。</li>
 * </ul>
 */
@AutoConfiguration(after = {BellringerAutoConfiguration.class, PipelineAutoConfiguration.class})
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class SourceAutoConfiguration {

    /** 统一出口：Logback root logger 挂载（无 logback＝静默跳过，宿主换 log4j2 也不炸）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    @ConditionalOnProperty(prefix = "observability.sources.error-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class ErrorLogConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ErrorLogAppender bellringerErrorLogAppender(AlertEventFactory events, AlertEventSink sink) {
            return new ErrorLogAppender(events, sink);
        }

        @Bean
        @ConditionalOnMissingBean
        public ErrorLogEventSource bellringerErrorLogEventSource(ErrorLogAppender appender) {
            return new ErrorLogEventSource(rootLoggerContext(), appender);
        }

        static LoggerContext rootLoggerContext() {
            ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (!(factory instanceof LoggerContext)) {
                throw new IllegalStateException("敲钟人：ERROR 出口需要 Logback 作为 SLF4J 实现，当前为 "
                        + factory.getClass().getName() + "（可关闭 observability.sources.error-log.enabled）");
            }
            return (LoggerContext) factory;
        }
    }

    /** 慢 SQL：宿主引入 MyBatis 才装配（observability.slow-sql.*）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    @ConditionalOnProperty(prefix = "observability.slow-sql", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class SlowSqlConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SlowSqlInterceptor bellringerSlowSqlInterceptor(BellringerProperties properties,
                                                              AlertEventFactory events, AlertEventSink sink) {
            return new SlowSqlInterceptor(properties, events, sink);
        }
    }

    /** 锁超时/死锁：宿主引入 MyBatis 才装配（observability.sources.lock-timeout.enabled）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    @ConditionalOnProperty(prefix = "observability.sources.lock-timeout", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class LockTimeoutConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public LockTimeoutInterceptor bellringerLockTimeoutInterceptor(AlertEventFactory events,
                                                                      AlertEventSink sink) {
            return new LockTimeoutInterceptor(events, sink);
        }
    }

    /** 定时任务失败：增量定制宿主 TaskScheduler 的错误处理，不自建调度器。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TaskSchedulerCustomizer.class)
    @ConditionalOnProperty(prefix = "observability.sources.scheduled-task", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class ScheduledTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ScheduledTaskErrorHandler bellringerScheduledTaskErrorHandler(AlertEventFactory events,
                                                                            AlertEventSink sink) {
            return new ScheduledTaskErrorHandler(events, sink);
        }

        @Bean
        @ConditionalOnMissingBean
        public TaskSchedulerCustomizer bellringerScheduledTaskErrorCustomizer(ScheduledTaskErrorHandler handler) {
            return new ScheduledTaskErrorCustomizer(handler);
        }
    }

    /** 健康自检：配置了可用目标才启动（目标为空＝静默跳过，不占线程）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.context.SmartLifecycle")
    @ConditionalOnProperty(prefix = "observability.sources.health-check", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @Conditional(HasHealthTargetsCondition.class)
    static class HealthCheckConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public HealthCheckTask bellringerHealthCheckTask(BellringerProperties properties, AppIdentity identity,
                                                        AlertEventFactory events, AlertEventSink sink) {
            return new HealthCheckTask(properties.getSources().getHealthCheck(), identity, events, sink);
        }
    }

    /** 访问日志：默认关闭；开启需要 spring-web（servlet 过滤器）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnProperty(prefix = "observability.sources.access-log", name = "enabled", havingValue = "true")
    static class AccessLogConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AccessLogFilter bellringerAccessLogFilter(BellringerProperties properties,
                                                        AlertEventFactory events, AlertEventSink sink) {
            return new AccessLogFilter(properties.getSources().getAccessLog(), events, sink);
        }
    }
}
