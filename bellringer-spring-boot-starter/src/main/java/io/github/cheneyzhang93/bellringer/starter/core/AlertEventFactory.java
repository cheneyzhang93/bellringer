package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;

/**
 * 事件工厂（应用内埋点入口）：把 F5 应用标识预填进 Builder，宿主与事件源共用一套身份口径。
 *
 * <p>宿主可用它上报自定义业务告警：
 * <pre>
 *   alertEventFactory.builder("order-stuck").level(AlertLevel.P1).title("订单滞留").build()
 * </pre>
 * 构造完成后交给 {@link AlertEventSink#emit}。
 */
public class AlertEventFactory {

    private final AppIdentity identity;

    public AlertEventFactory(AppIdentity identity) {
        this.identity = identity;
    }

    /** 已预填 app/instance/env/occurredAt 的事件构造器。 */
    public AlertEvent.Builder builder(String type) {
        return AlertEvent.builder()
                .type(type)
                .occurredAt(System.currentTimeMillis())
                .app(identity.getApp())
                .instance(identity.getInstance())
                .env(identity.getEnv());
    }

    public AppIdentity identity() {
        return identity;
    }
}
