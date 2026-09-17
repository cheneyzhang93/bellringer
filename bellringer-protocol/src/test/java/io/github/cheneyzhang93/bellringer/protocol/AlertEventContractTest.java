package io.github.cheneyzhang93.bellringer.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AlertEvent v1 契约语义：缺省值、校验规则、传输格式（F4）。 */
class AlertEventContractTest {

    private static AlertEvent validEvent() {
        return AlertEvent.builder()
                .type(EventTypes.SYSTEM_ERROR)
                .title("订单提交失败")
                .app("checkout-demo")
                .build();
    }

    private static AlertEvent eventWithType(String type) {
        return AlertEvent.builder().type(type).title("t").app("a").build();
    }

    private static String repeat(char ch, int count) {
        return new String(new char[count]).replace('\0', ch);
    }

    @Test
    void builderFillsFrozenDefaults() {
        AlertEvent event = validEvent();
        assertEquals(AlertLevel.P2, event.getLevel());
        assertTrue(event.getOccurredAt() > 0L && event.getOccurredAt() <= System.currentTimeMillis());
        assertNotNull(event.getEventId());
        assertTrue(event.validate().isEmpty(), String.valueOf(event.validate()));
    }

    @Test
    void builderGeneratesDistinctEventIdsAndKeepsExplicitOnes() {
        assertNotEquals(validEvent().getEventId(), validEvent().getEventId());

        AlertEvent explicit = AlertEvent.builder()
                .type(EventTypes.SYSTEM_ERROR)
                .title("t")
                .app("a")
                .eventId("9f1d1a5e-0b2c-4c3d-8e4f-1a2b3c4d5e6f")
                .build();
        assertEquals("9f1d1a5e-0b2c-4c3d-8e4f-1a2b3c4d5e6f", explicit.getEventId());
    }

    @Test
    void validationFlagsMissingRequiredFields() {
        List<String> violations = new AlertEvent().validate();
        assertEquals(5, violations.size(), violations.toString());
        String joined = violations.toString();
        assertTrue(joined.contains("type"));
        assertTrue(joined.contains("title"));
        assertTrue(joined.contains("app"));
        assertTrue(joined.contains("eventId"));
        assertTrue(joined.contains("occurredAt"));
    }

    @Test
    void typeMustBeKebabCaseWithinLimit() {
        assertTrue(eventWithType("system-error").validate().isEmpty());
        assertTrue(eventWithType("a").validate().isEmpty());
        assertTrue(eventWithType("slow-sql-v2").validate().isEmpty());
        assertFalse(eventWithType("System-Error").validate().isEmpty());
        assertFalse(eventWithType("system_error").validate().isEmpty());
        assertFalse(eventWithType("system--error").validate().isEmpty());
        assertFalse(eventWithType("-system").validate().isEmpty());
        assertFalse(eventWithType("system-").validate().isEmpty());
        assertFalse(eventWithType(repeat('a', AlertEvent.MAX_TYPE_LENGTH + 1)).validate().isEmpty());
    }

    @Test
    void traceIdMustBeLowercase32HexWhenPresent() {
        AlertEvent event = validEvent();
        assertTrue(event.validate().isEmpty());

        event.setTraceId("4bf92f3577b34da6a3ce929d0e0e4736");
        assertTrue(event.validate().isEmpty());

        event.setTraceId("4BF92F3577B34DA6A3CE929D0E0E4736");
        assertFalse(event.validate().isEmpty());

        event.setTraceId("4bf92f3577b34da6a3ce929d0e0e473");
        assertFalse(event.validate().isEmpty());

        event.setTraceId(null);
        assertTrue(event.validate().isEmpty());
    }

    @Test
    void instanceMustFollowHostnamePidWhenPresent() {
        AlertEvent event = validEvent();
        event.setInstance("checkout-demo-1:48217");
        assertTrue(event.validate().isEmpty());

        event.setInstance("127.0.0.1:8080");
        assertTrue(event.validate().isEmpty());

        event.setInstance("no-pid");
        assertFalse(event.validate().isEmpty());

        event.setInstance("host:pid");
        assertFalse(event.validate().isEmpty());

        event.setInstance("   ");
        assertTrue(event.validate().isEmpty());
    }

    @Test
    void eventIdMustBeUuid() {
        AlertEvent event = validEvent();
        event.setEventId("not-a-uuid");
        List<String> violations = event.validate();
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("UUID"));
    }

    @Test
    void unknownLevelValueIsRejectedOnParse() {
        ObjectMapper mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        String json = "{\"type\":\"system-error\",\"level\":\"P3\",\"title\":\"t\",\"app\":\"a\","
                + "\"eventId\":\"9f1d1a5e-0b2c-4c3d-8e4f-1a2b3c4d5e6f\",\"occurredAt\":1789725600000}";
        assertThrows(InvalidFormatException.class, () -> mapper.readValue(json, AlertEvent.class));
    }

    @Test
    void occurredAtTravelsAsEpochMillisNumber() {
        AlertEvent event = validEvent();
        event.setOccurredAt(1789725600000L);
        JsonNode node = JsonMapper.builder().build().valueToTree(event);
        assertTrue(node.get("occurredAt").isNumber());
        assertEquals(1789725600000L, node.get("occurredAt").asLong());
    }

    @Test
    void payloadIsDefensivelyCopied() {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("key", "v");
        AlertEvent event = AlertEvent.builder()
                .type(EventTypes.SYSTEM_ERROR)
                .title("t")
                .app("a")
                .payload(source)
                .build();

        source.put("key", "mutated");
        assertEquals("v", event.getPayload().get("key"));
    }
}
