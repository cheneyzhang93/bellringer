package io.github.cheneyzhang93.bellringer.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约 fixture 测试：fixtures/v1 即两仓共同基准（F1/F2/F4），此处置于主资源随构件发布，
 * 控制台测试从 classpath 读取同一份文件，不允许各自维护副本。
 */
class ContractFixturesTest {

    /** 与两侧消费端等价：Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES。 */
    private static ObjectMapper tolerantMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /** fixtures 基准序列化：null 字段省略（规范化约定）。 */
    private static ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    private static InputStream fixture(String name) {
        InputStream in = ContractFixturesTest.class.getResourceAsStream("/fixtures/v1/" + name);
        assertNotNull(in, "fixture 不存在：" + name);
        return in;
    }

    @Test
    void fullEventRequestMatchesFixture() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("orderId", "ORD-20260918-0001");
        payload.put("channel", "app");
        payload.put("attempt", 3);
        payload.put("retryable", false);

        AlertEvent event = AlertEvent.builder()
                .type(EventTypes.SYSTEM_ERROR)
                .level(AlertLevel.P1)
                .title("订单提交失败")
                .message("`OrderService.submit` 抛出 NullPointerException：订单 ORD-20260918-0001 分单失败")
                .source("com.example.demo.order.OrderService.submit")
                .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
                .aggregateKey("OrderService.submit:NullPointerException")
                .occurredAt(1789725600000L)
                .app("checkout-demo")
                .instance("checkout-demo-1:48217")
                .env("prod")
                .eventId("9f1d1a5e-0b2c-4c3d-8e4f-1a2b3c4d5e6f")
                .payload(payload)
                .build();
        assertTrue(event.validate().isEmpty(), String.valueOf(event.validate()));

        ObjectMapper mapper = canonicalMapper();
        JsonNode actual = mapper.valueToTree(EventEnvelope.of("1.0.0", event));
        JsonNode expected = mapper.readTree(fixture("event-request-full.json"));
        assertEquals(expected, actual);
    }

    @Test
    void minimalEventRequestUsesFrozenDefaults() throws IOException {
        EventEnvelope envelope = tolerantMapper().readValue(fixture("event-request-minimal.json"), EventEnvelope.class);
        assertTrue(envelope.validate().isEmpty(), String.valueOf(envelope.validate()));

        AlertEvent event = envelope.getEvent();
        assertEquals(AlertLevel.P2, event.getLevel());
        assertNull(event.getMessage());
        assertNull(event.getSource());
        assertNull(event.getTraceId());
        assertNull(event.getAggregateKey());
        assertNull(event.getInstance());
        assertNull(event.getEnv());
        assertNull(event.getPayload());
        assertTrue(event.validate().isEmpty(), String.valueOf(event.validate()));

        ObjectMapper mapper = canonicalMapper();
        ObjectNode expected = (ObjectNode) mapper.readTree(fixture("event-request-minimal.json"));
        ((ObjectNode) expected.path("event")).put("level", "P2");
        assertEquals(expected, mapper.valueToTree(envelope));
    }

    @Test
    void unknownFieldsAreToleratedAndDropped() throws IOException {
        EventEnvelope envelope = tolerantMapper()
                .readValue(fixture("event-request-forward-compat.json"), EventEnvelope.class);
        assertEquals(1, envelope.getSchemaVersion());
        assertEquals("2.0.0", envelope.getSdkVersion());

        AlertEvent event = envelope.getEvent();
        assertEquals(EventTypes.SLOW_SQL, event.getType());
        assertEquals(Long.valueOf(1789729200000L), Long.valueOf(event.getOccurredAt()));
        assertEquals(Integer.valueOf(3821), event.getPayload().get("elapsedMillis"));
        assertTrue(event.validate().isEmpty(), String.valueOf(event.validate()));

        JsonNode reserialized = canonicalMapper().valueToTree(envelope);
        assertFalse(reserialized.has("futureEnvelopeField"));
        assertFalse(reserialized.path("event").has("futureEventField"));
    }

    @Test
    void heartbeatRequestMatchesFixture() throws IOException {
        Map<String, Boolean> modules = new LinkedHashMap<String, Boolean>();
        modules.put("alert", true);
        modules.put("slowSql", true);
        modules.put("trace", false);
        modules.put("metrics", true);

        InstanceHeartbeat heartbeat = InstanceHeartbeat.builder()
                .app("checkout-demo")
                .instance("checkout-demo-1:48217")
                .version("2.4.1")
                .env("prod")
                .startedAt(1789722000000L)
                .modules(modules)
                .build();
        assertTrue(heartbeat.validate().isEmpty(), String.valueOf(heartbeat.validate()));

        ObjectMapper mapper = canonicalMapper();
        JsonNode actual = mapper.valueToTree(HeartbeatEnvelope.of("1.0.0", heartbeat));
        JsonNode expected = mapper.readTree(fixture("heartbeat-request.json"));
        assertEquals(expected, actual);
    }

    @Test
    void heartbeatParsesFixtureValues() throws IOException {
        HeartbeatEnvelope envelope = tolerantMapper()
                .readValue(fixture("heartbeat-request.json"), HeartbeatEnvelope.class);
        assertTrue(envelope.validate().isEmpty(), String.valueOf(envelope.validate()));

        InstanceHeartbeat heartbeat = envelope.getHeartbeat();
        assertEquals("checkout-demo-1:48217", heartbeat.getInstance());
        assertEquals(1789722000000L, heartbeat.getStartedAt());
        assertEquals(Boolean.FALSE, heartbeat.getModules().get("trace"));
        assertEquals(Boolean.TRUE, heartbeat.getModules().get("slowSql"));
        assertTrue(heartbeat.validate().isEmpty(), String.valueOf(heartbeat.validate()));
    }
}
