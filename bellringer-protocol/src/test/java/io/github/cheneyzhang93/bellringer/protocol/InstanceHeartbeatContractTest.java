package io.github.cheneyzhang93.bellringer.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 实例心跳契约语义：必填项、instance 规则、modules 映射（F2）。 */
class InstanceHeartbeatContractTest {

    private static InstanceHeartbeat validHeartbeat() {
        return InstanceHeartbeat.builder()
                .app("checkout-demo")
                .instance(InstanceIds.of("checkout-demo-1", 48217L))
                .version("2.4.1")
                .env("prod")
                .startedAt(1789722000000L)
                .build();
    }

    @Test
    void builderProducesValidHeartbeat() {
        InstanceHeartbeat heartbeat = validHeartbeat();
        assertEquals("checkout-demo-1:48217", heartbeat.getInstance());
        assertTrue(heartbeat.validate().isEmpty(), String.valueOf(heartbeat.validate()));
    }

    @Test
    void validationFlagsMissingAppInstanceStartedAt() {
        List<String> violations = new InstanceHeartbeat().validate();
        assertEquals(3, violations.size(), violations.toString());
        String joined = violations.toString();
        assertTrue(joined.contains("app"));
        assertTrue(joined.contains("instance"));
        assertTrue(joined.contains("startedAt"));
    }

    @Test
    void instanceRuleMatchesFrozenPattern() {
        assertTrue(InstanceIds.isValid("web-01:4242"));
        assertTrue(InstanceIds.isValid("127.0.0.1:8080"));
        assertTrue(InstanceIds.isValid("host:0"));
        assertFalse(InstanceIds.isValid("host"));
        assertFalse(InstanceIds.isValid("host:"));
        assertFalse(InstanceIds.isValid("host:abc"));
        assertFalse(InstanceIds.isValid(null));
        assertEquals("web-01:4242", InstanceIds.of("web-01", 4242L));

        InstanceHeartbeat heartbeat = validHeartbeat();
        heartbeat.setInstance("no-pid");
        assertFalse(heartbeat.validate().isEmpty());
    }

    @Test
    void startedAtMustBeSetExplicitly() {
        InstanceHeartbeat heartbeat = validHeartbeat();
        heartbeat.setStartedAt(0L);
        List<String> violations = heartbeat.validate();
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("startedAt"));
    }

    @Test
    void modulesAreDefensivelyCopiedAndRoundTrip() throws IOException {
        Map<String, Boolean> modules = new LinkedHashMap<String, Boolean>();
        modules.put("alert", true);
        modules.put("trace", false);
        InstanceHeartbeat heartbeat = InstanceHeartbeat.builder()
                .app("checkout-demo")
                .instance(InstanceIds.of("checkout-demo-1", 48217L))
                .startedAt(1789722000000L)
                .modules(modules)
                .build();

        modules.put("alert", false);
        assertEquals(Boolean.TRUE, heartbeat.getModules().get("alert"));

        ObjectMapper mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        InstanceHeartbeat parsed = mapper.readValue(mapper.writeValueAsString(heartbeat), InstanceHeartbeat.class);
        assertEquals(Boolean.TRUE, parsed.getModules().get("alert"));
        assertEquals(Boolean.FALSE, parsed.getModules().get("trace"));
    }

    @Test
    void heartbeatEnvelopeCarriesSchemaVersionAndSdkVersion() {
        HeartbeatEnvelope envelope = HeartbeatEnvelope.of("1.0.0", validHeartbeat());
        assertEquals(EventEnvelope.SCHEMA_VERSION, envelope.getSchemaVersion());
        assertTrue(envelope.validate().isEmpty(), String.valueOf(envelope.validate()));

        List<String> violations = HeartbeatEnvelope.of(null, null).validate();
        assertEquals(2, violations.size(), violations.toString());
    }
}
