package io.github.cheneyzhang93.bellringer.starter.testing;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;

/** 测试事件工厂：字段齐备且合 F4 契约。 */
public final class TestEvents {

    private TestEvents() {
    }

    public static AlertEvent.Builder sample(String app) {
        return AlertEvent.builder()
                .type(EventTypes.SYSTEM_ERROR)
                .level(AlertLevel.P1)
                .title("单元测试事件")
                .message("异常摘要：boom")
                .source("com.example.DemoService#run")
                .occurredAt(System.currentTimeMillis())
                .app(app)
                .instance("host-1:123")
                .env("test");
    }

    public static AlertEvent valid(String app) {
        return sample(app).build();
    }

    public static AlertEvent valid(String app, String type, String aggregateKey) {
        return sample(app).type(type).aggregateKey(aggregateKey).build();
    }
}
