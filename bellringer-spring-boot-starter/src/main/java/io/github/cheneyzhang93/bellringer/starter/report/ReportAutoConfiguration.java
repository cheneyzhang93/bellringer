package io.github.cheneyzhang93.bellringer.starter.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ApplicationStartedAt;
import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.core.LocalAlertPusher;
import io.github.cheneyzhang93.bellringer.starter.core.ReportOutlet;
import io.github.cheneyzhang93.bellringer.starter.engine.PipelineAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 上报出口装配（F1 事件上报 + F2 实例心跳，mode 含 http 时才生效）。
 *
 * <p>配置校验（{@link ReportSettings}）常驻：mode 含 http 而 endpoint/token 缺失＝显式配置错误，
 * 无论 Jackson 是否在 classpath 都启动即失败；Jackson 缺失时额外 ERROR 告警一次。
 */
@AutoConfiguration(after = {BellringerAutoConfiguration.class, PipelineAutoConfiguration.class})
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true")
public class ReportAutoConfiguration {

    @Bean
    public ReportSettings bellringerReportSettings(BellringerProperties properties) {
        return ReportSettings.from(properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObjectMapper.class)
    @Conditional(OnReportHttpCondition.class)
    static class HttpReportConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ReportState bellringerReportState() {
            return new ReportState();
        }

        @Bean
        @ConditionalOnMissingBean
        public ReportClient bellringerReportClient(ReportSettings settings, ReportState state,
                                                   ObjectProvider<LocalAlertPusher> localPusher,
                                                   AppIdentity identity) {
            return new ReportClient(settings, state, localPusher, identity);
        }

        @Bean
        @ConditionalOnMissingBean
        public HttpEventReporter bellringerHttpEventReporter(ReportSettings settings, ReportClient client,
                                                             ReportState state, AppIdentity identity) {
            return new HttpEventReporter(settings, client, state, identity);
        }

        @Bean(destroyMethod = "destroy")
        public HeartbeatTask bellringerHeartbeatTask(ReportSettings settings, ReportClient client, ReportState state,
                                                     AppIdentity identity, ApplicationStartedAt startedAt,
                                                     BellringerProperties properties, Environment environment) {
            return new HeartbeatTask(settings, client, state, identity, startedAt, properties, environment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
    @Conditional(OnReportHttpCondition.class)
    static class MissingJacksonConfiguration {

        @Bean
        public JacksonMissingWarner bellringerJacksonMissingWarner() {
            return new JacksonMissingWarner();
        }
    }
}
