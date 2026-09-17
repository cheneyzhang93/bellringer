package io.github.cheneyzhang93.bellringer.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 跨仓冻结面 pin 测试：路径、鉴权头、schemaVersion、内置事件类型（F1/F2/F4）。 */
class ApiContractTest {

    @Test
    void frozenApiPathsAndAuthHeader() {
        assertEquals("/api/v1/events", ApiPaths.EVENTS);
        assertEquals("/api/v1/instances/heartbeat", ApiPaths.INSTANCE_HEARTBEAT);
        assertEquals("Authorization", ApiPaths.AUTHORIZATION_HEADER);
        assertEquals("Bearer ", ApiPaths.BEARER_PREFIX);
    }

    @Test
    void frozenSchemaVersion() {
        assertEquals(1, EventEnvelope.SCHEMA_VERSION);
        assertEquals(1, new EventEnvelope().getSchemaVersion());
        assertEquals(1, new HeartbeatEnvelope().getSchemaVersion());
    }

    @Test
    void frozenEventTypes() {
        assertEquals("system-error", EventTypes.SYSTEM_ERROR);
        assertEquals("slow-sql", EventTypes.SLOW_SQL);
        assertEquals("lock-timeout", EventTypes.LOCK_TIMEOUT);
        assertEquals("scheduled-task-error", EventTypes.SCHEDULED_TASK_ERROR);
        assertEquals("health-check", EventTypes.HEALTH_CHECK);
        assertEquals("access-log", EventTypes.ACCESS_LOG);
    }

    @Test
    void eventEnvelopeValidation() {
        AlertEvent event = AlertEvent.builder().type(EventTypes.SYSTEM_ERROR).title("t").app("a").build();
        assertTrue(EventEnvelope.of("1.0.0", event).validate().isEmpty());
        assertFalse(EventEnvelope.of("1.0.0", null).validate().isEmpty());
        assertFalse(EventEnvelope.of(null, event).validate().isEmpty());

        EventEnvelope futureSchema = EventEnvelope.of("1.0.0", event);
        futureSchema.setSchemaVersion(2);
        assertTrue(futureSchema.validate().get(0).contains("schemaVersion"));
    }
}
