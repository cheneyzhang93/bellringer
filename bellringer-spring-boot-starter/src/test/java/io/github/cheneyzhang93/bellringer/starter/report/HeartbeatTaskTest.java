package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ApplicationStartedAt;
import io.github.cheneyzhang93.bellringer.starter.core.LocalAlertPusher;
import io.github.cheneyzhang93.bellringer.starter.testing.StubHttpServer;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实例心跳验收（F2）：路径与信封字段、startedAt 语义、失败静默续拍、401 停通道后停拍。
 */
class HeartbeatTaskTest {

    private static final long STARTED_AT = 1700000000000L;

    private StubHttpServer server;

    private BellringerProperties properties;

    private AppIdentity identity;

    private ReportState state;

    private ReportClient client;

    private ReportSettings settings;

    private HeartbeatTask task;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
        properties = new BellringerProperties();
        properties.setApp("demo");
        properties.setEnv("test");
        properties.setVersion("9.9.9");
        properties.getReport().setMode(BellringerProperties.Report.Mode.HTTP);
        properties.getReport().setEndpoint(server.url());
        properties.getReport().setToken("tk");
        properties.getReport().setTimeoutMillis(2000);
        properties.getReport().setHeartbeatIntervalSeconds(1);
        identity = AppIdentity.resolve(properties, new MockEnvironment());
        state = new ReportState();
        settings = ReportSettings.from(properties);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("localPusher", new LocalAlertPusher() {
            @Override
            public void pushLocal(AlertEvent event) {
                // 心跳测试不触发本地告警
            }
        });
        client = new ReportClient(settings, state, factory.getBeanProvider(LocalAlertPusher.class), identity);
    }

    @AfterEach
    void tearDown() {
        task.destroy();
        server.close();
    }

    @Test
    void beatsWithFrozenFields() throws Exception {
        task = new HeartbeatTask(settings, client, state, identity, new ApplicationStartedAt(STARTED_AT),
                properties, new MockEnvironment());

        waitUntil("首拍", requestCountAtLeast(1));
        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.method).isEqualTo("POST");
        assertThat(request.path).isEqualTo("/api/v1/instances/heartbeat");
        assertThat(request.authorization).isEqualTo("Bearer tk");
        assertThat(request.body).contains("\"schemaVersion\":1");
        assertThat(request.body).contains("\"sdkVersion\":\"" + SdkVersion.current() + "\"");
        assertThat(request.body).contains("\"app\":\"demo\"");
        assertThat(request.body).contains("\"instance\":\"" + identity.getInstance() + "\"");
        assertThat(request.body).contains("\"version\":\"9.9.9\"");
        assertThat(request.body).contains("\"env\":\"test\"");
        assertThat(request.body).contains("\"startedAt\":" + STARTED_AT);
        assertThat(request.body).contains("\"modules\":{");
        assertThat(request.body).contains("\"report\":true");
        assertThat(request.body).contains("\"alert-dingtalk\":false");
        assertThat(request.body).contains("\"source-slow-sql\":true");
    }

    @Test
    void repeatedBeatsFollowConfiguredInterval() throws Exception {
        task = new HeartbeatTask(settings, client, state, identity, new ApplicationStartedAt(STARTED_AT),
                properties, new MockEnvironment());

        waitUntil("首拍", requestCountAtLeast(1));
        waitUntil("第二拍", requestCountAtLeast(2));
    }

    @Test
    void stoppedChannelSilencesHeartbeat() throws Exception {
        task = new HeartbeatTask(settings, client, state, identity, new ApplicationStartedAt(STARTED_AT),
                properties, new MockEnvironment());
        waitUntil("首拍", requestCountAtLeast(1));

        state.stop("测试停止");
        int count = server.requestCount();
        Thread.sleep(2500);

        assertThat(server.requestCount()).isEqualTo(count);
    }

    @Test
    void destroyStopsScheduling() throws Exception {
        task = new HeartbeatTask(settings, client, state, identity, new ApplicationStartedAt(STARTED_AT),
                properties, new MockEnvironment());
        waitUntil("首拍", requestCountAtLeast(1));

        task.destroy();
        int count = server.requestCount();
        Thread.sleep(2500);

        assertThat(server.requestCount()).isEqualTo(count);
    }

    @Test
    void sendFailureIsSilentAndNextBeatContinues() throws Exception {
        server.enqueue(500, "{\"error\":\"boom\"}");
        task = new HeartbeatTask(settings, client, state, identity, new ApplicationStartedAt(STARTED_AT),
                properties, new MockEnvironment());

        waitUntil("首拍失败", requestCountAtLeast(1));
        waitUntil("下一拍补报", requestCountAtLeast(2));
        assertThat(state.isStopped()).isFalse();
    }

    private BooleanSupplier requestCountAtLeast(final int expected) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return server.requestCount() >= expected;
            }
        };
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
