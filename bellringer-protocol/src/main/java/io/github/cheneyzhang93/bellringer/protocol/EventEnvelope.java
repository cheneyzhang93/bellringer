package io.github.cheneyzhang93.bellringer.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件上报信封（F1 冻结）：{@code POST /api/v1/events} 请求体
 * {@code {"schemaVersion":1,"sdkVersion":"x.y.z","event":{AlertEvent v1}}}。
 *
 * <p>schemaVersion 为主版本，仅在破坏性变更时递增；控制台只接收已知主版本。
 * 消费端用默认 Jackson 映射即可（需容忍未知字段，Boot 默认已关闭 FAIL_ON_UNKNOWN_PROPERTIES）。
 */
public class EventEnvelope {

    /** 当前协议主版本（F1）。 */
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion = SCHEMA_VERSION;

    /** starter 构件版本，用于兼容矩阵与问题定位。 */
    private String sdkVersion;

    private AlertEvent event;

    public EventEnvelope() {
    }

    /** 以当前主版本构造信封。 */
    public static EventEnvelope of(String sdkVersion, AlertEvent event) {
        EventEnvelope envelope = new EventEnvelope();
        envelope.sdkVersion = sdkVersion;
        envelope.event = event;
        return envelope;
    }

    /** 校验信封完整性，返回全部违规描述；空列表＝合法。 */
    public List<String> validate() {
        List<String> violations = new ArrayList<String>();
        if (schemaVersion != SCHEMA_VERSION) {
            violations.add("schemaVersion 不受支持：期望 " + SCHEMA_VERSION + "，实际 " + schemaVersion);
        }
        if (sdkVersion == null || sdkVersion.trim().isEmpty()) {
            violations.add("sdkVersion 缺失");
        }
        if (event == null) {
            violations.add("event 缺失");
        }
        return violations;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public AlertEvent getEvent() {
        return event;
    }

    public void setEvent(AlertEvent event) {
        this.event = event;
    }
}
