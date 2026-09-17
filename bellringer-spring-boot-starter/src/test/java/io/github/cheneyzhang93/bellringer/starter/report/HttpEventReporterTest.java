package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.ApiPaths;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.LocalAlertPusher;
import io.github.cheneyzhang93.bellringer.starter.testing.StubHttpServer;
import io.github.cheneyzhang93.bellringer.starter.testing.TestEvents;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全量上报器验收（F1）：信封形状、Bearer 认证、退避重试、4xx 放弃、401 停通道 + 本地告警、队列满丢弃计数。
 */
class HttpEventReporterTest {

    private StubHttpServer server;

    private BellringerProperties properties;

    private AppIdentity identity;

    private ReportState state;

    private ReportClient client;

    private List<AlertEvent> localAlerts;

    private ReportSettings settings;

    private HttpEventReporter reporter;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
        properties = new BellringerProperties();
        properties.setApp("demo");
        properties.setEnv("test");
        properties.getReport().setMode(BellringerProperties.Report.Mode.HTTP);
        properties.getReport().setEndpoint(server.url());
        properties.getReport().setToken("tk");
        properties.getReport().setTimeoutMillis(2000);
        properties.getReport().setMaxRetries(1);
        properties.getReport().setQueueSize(8);
        identity = AppIdentity.resolve(properties, new MockEnvironment());
        state = new ReportState();
        localAlerts = new CopyOnWriteArrayList<AlertEvent>();
        client = newClient(ReportSettings.from(properties));
        settings = ReportSettings.from(properties);
        reporter = new HttpEventReporter(settings, client, state, identity);
    }

    @AfterEach
    void tearDown() {
        reporter.destroy();
        server.close();
    }

    @Test
    void deliversEnvelopeWithBearerAuth() throws Exception {
        AlertEvent event = TestEvents.valid("demo");

        reporter.enqueue(event);

        waitUntil("首次上报", () -> server.requestCount() >= 1);
        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.method).isEqualTo("POST");
        assertThat(request.path).isEqualTo("/api/v1/events");
        assertThat(request.authorization).isEqualTo("Bearer tk");
        assertThat(request.contentType).contains("application/json");
        assertThat(request.body).contains("\"schemaVersion\":1");
        assertThat(request.body).contains("\"sdkVersion\":\"" + SdkVersion.current() + "\"");
        assertThat(request.body).contains("\"eventId\":\"" + event.getEventId() + "\"");
        assertThat(request.body).contains("\"type\":\"system-error\"");
        assertThat(request.body).contains("\"level\":\"P1\"");
        assertThat(request.body).contains("\"app\":\"demo\"");
    }

    @Test
    void retriesOnServerErrorThenSucceeds() throws Exception {
        server.enqueue(500, "{\"error\":\"boom\"}");
        server.enqueue(202, "{}");

        reporter.enqueue(TestEvents.valid("demo"));

        waitUntil("退避重试成功", () -> server.requestCount() >= 2);
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(state.isStopped()).isFalse();
    }

    @Test
    void tokenInvalidStopsChannelAndAlertsLocallyOnce() throws Exception {
        server.enqueue(401, "{\"error\":\"unauthorized\"}");

        reporter.enqueue(TestEvents.valid("demo"));

        waitUntil("401 停止通道", () -> state.isStopped());
        waitUntil("本地告警一次", () -> localAlerts.size() == 1);
        AlertEvent local = localAlerts.get(0);
        assertThat(local.getType()).isEqualTo("system-error");
        assertThat(local.getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(local.getAggregateKey()).isEqualTo("report-token-invalid");
        assertThat(local.getApp()).isEqualTo("demo");

        assertThat(client.post(ApiPaths.EVENTS, "{}")).isEqualTo(ReportClient.NOT_SENT);
        reporter.enqueue(TestEvents.valid("demo"));
        assertThat(reporter.getQueueDepth()).isZero();
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void clientErrorIsDroppedWithoutRetry() throws Exception {
        server.enqueue(400, "{\"error\":\"bad request\"}");

        reporter.enqueue(TestEvents.valid("demo"));

        waitUntil("单次尝试", () -> server.requestCount() >= 1);
        Thread.sleep(300);
        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(state.isStopped()).isFalse();
        assertThat(reporter.getQueueDepth()).isZero();
    }

    @Test
    void queueOverflowDropsAndCountsWithoutBlocking() throws Exception {
        properties.getReport().setQueueSize(1);
        properties.getReport().setTimeoutMillis(5000);
        properties.getReport().setMaxRetries(0);
        ReportSettings tightSettings = ReportSettings.from(properties);
        client = newClient(tightSettings);
        HttpEventReporter tight = new HttpEventReporter(tightSettings, client, state, identity);
        server.enqueue(200, "{\"ok\":true}", 3000L);

        try {
            for (int i = 0; i < 5; i++) {
                tight.enqueue(TestEvents.valid("demo"));
            }

            assertThat(tight.getDroppedCount()).isGreaterThanOrEqualTo(1);
        } finally {
            tight.destroy();
        }
    }

    private ReportClient newClient(ReportSettings reportSettings) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("localPusher", new LocalAlertPusher() {
            @Override
            public void pushLocal(AlertEvent event) {
                localAlerts.add(event);
            }
        });
        return new ReportClient(reportSettings, state, factory.getBeanProvider(LocalAlertPusher.class), identity);
    }

    private static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("等待超时：" + what);
    }
}
