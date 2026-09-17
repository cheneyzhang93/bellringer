package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.starter.core.BellringerAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.engine.FallbackDedupAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.engine.PipelineAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.outlet.FallbackSenderAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.outlet.SenderAutoConfiguration;
import io.github.cheneyzhang93.bellringer.starter.testing.StubHttpServer;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上报装配验收：mode=webhook 零 HTTP 通道、mode 含 http 缺配置快速失败、齐全时装配上报器与心跳。
 */
class ReportAutoConfigurationTest {

    private StubHttpServer server;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BellringerAutoConfiguration.class,
                    PipelineAutoConfiguration.class, FallbackDedupAutoConfiguration.class,
                    SenderAutoConfiguration.class, FallbackSenderAutoConfiguration.class,
                    ReportAutoConfiguration.class));

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void webhookModeAssemblesNoHttpChannel() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReportSettings.class);
                    assertThat(context.getBean(ReportSettings.class).isHttpEnabled()).isFalse();
                    assertThat(context).doesNotHaveBean(HttpEventReporter.class);
                    assertThat(context).doesNotHaveBean(HeartbeatTask.class);
                });
    }

    @Test
    void httpModeWithoutEndpointFailsFast() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.report.mode=http", "observability.report.token=tk")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("endpoint");
                });
    }

    @Test
    void httpModeAssemblesReporterAndHeartbeat() {
        runner.withPropertyValues("observability.enabled=true", "observability.app=demo",
                        "observability.report.mode=both", "observability.report.endpoint=" + server.url(),
                        "observability.report.token=tk")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ReportSettings.class).isHttpEnabled()).isTrue();
                    assertThat(context).hasSingleBean(ReportState.class);
                    assertThat(context).hasSingleBean(ReportClient.class);
                    assertThat(context).hasSingleBean(HttpEventReporter.class);
                    assertThat(context).hasSingleBean(HeartbeatTask.class);
                });
    }

    @Test
    void disabledAssemblesNoReportBeans() {
        runner.withPropertyValues("observability.app=demo")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ReportSettings.class);
                    assertThat(context).doesNotHaveBean(HttpEventReporter.class);
                });
    }
}
