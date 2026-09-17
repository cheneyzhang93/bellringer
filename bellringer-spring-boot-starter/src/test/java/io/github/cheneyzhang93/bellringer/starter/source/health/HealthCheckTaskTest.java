package io.github.cheneyzhang93.bellringer.starter.source.health;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.StubHttpServer;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康自检验收：连续失败判失联只报一次、恢复报一次、抖动抑制、无目标零启动。
 */
class HealthCheckTaskTest {

    private final RecordingSink sink = new RecordingSink();

    private final BellringerProperties.Sources.HealthCheck config = new BellringerProperties.Sources.HealthCheck();

    private StubHttpServer server;

    private HealthCheckTask task;

    @AfterEach
    void cleanup() {
        if (task != null) {
            task.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void downThenRecoveredEmitsP1ThenP2AndStaysQuiet() throws Exception {
        server = new StubHttpServer();
        server.enqueue(500, "{\"error\":\"db down\"}");
        server.enqueue(500, "{\"error\":\"db down\"}");
        start(target("order-db", server.url() + "/health"), 2);

        assertThat(sink.awaitCount(1)).isTrue();
        AlertEvent down = sink.first();
        assertThat(down.getType()).isEqualTo(EventTypes.HEALTH_CHECK);
        assertThat(down.getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(down.getTitle()).isEqualTo("健康自检失败：order-db");
        assertThat(down.getAggregateKey()).isEqualTo("down|order-db");
        assertThat(down.getMessage()).contains("HTTP 500").contains("连续 2 次");

        assertThat(sink.awaitCount(2)).isTrue();
        AlertEvent recovered = sink.events().get(1);
        assertThat(recovered.getLevel()).isEqualTo(AlertLevel.P2);
        assertThat(recovered.getTitle()).isEqualTo("健康自检恢复：order-db");
        assertThat(recovered.getAggregateKey()).isEqualTo("up|order-db");

        // 恢复后持续存活：状态迁移才报事件，不再重复轰炸
        Thread.sleep(2200L);
        assertThat(sink.count()).isEqualTo(2);
    }

    @Test
    void failuresBelowThresholdStaySilent() throws Exception {
        server = new StubHttpServer();
        server.enqueue(502, "bad gateway");
        start(target("order-db", server.url() + "/health"), 3);

        Thread.sleep(2500L);

        // 一次失败不足阈值：不报事件（抖动抑制）
        assertThat(sink.count()).isZero();
    }

    @Test
    void emptyTargetsStartsNothing() {
        task = new HealthCheckTask(config, TestIdentities.app("demo"),
                new AlertEventFactory(TestIdentities.app("demo")), sink);

        task.start();

        assertThat(task.isRunning()).isFalse();
        task.stop();
    }

    @Test
    void probesAllConfiguredTargets() throws Exception {
        server = new StubHttpServer();
        server.enqueue(500, "{\"error\":\"down\"}");
        server.enqueue(500, "{\"error\":\"down\"}");
        start(Arrays.asList(target("first", server.url() + "/health"), target("second", server.url() + "/health")), 1);

        assertThat(sink.awaitCount(2)).isTrue();
        assertThat(server.requestCount()).isGreaterThanOrEqualTo(2);
        assertThat(sink.events()).extracting(AlertEvent::getAggregateKey)
                .contains("down|first", "down|second");
    }

    private void start(BellringerProperties.Sources.HealthCheck.Target target, int failureThreshold) {
        start(Collections.singletonList(target), failureThreshold);
    }

    private void start(List<BellringerProperties.Sources.HealthCheck.Target> targets, int failureThreshold) {
        config.setTargets(targets);
        config.setFailureThreshold(failureThreshold);
        config.setIntervalSeconds(1);
        config.setInitialDelaySeconds(0);
        task = new HealthCheckTask(config, TestIdentities.app("demo"),
                new AlertEventFactory(TestIdentities.app("demo")), sink);
        task.start();
    }

    private static BellringerProperties.Sources.HealthCheck.Target target(String name, String url) {
        BellringerProperties.Sources.HealthCheck.Target target = new BellringerProperties.Sources.HealthCheck.Target();
        target.setName(name);
        target.setUrl(url);
        target.setTimeoutMillis(1000);
        return target;
    }
}
