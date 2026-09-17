package io.github.cheneyzhang93.bellringer.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 实例心跳信封（F2 冻结）：{@code POST /api/v1/instances/heartbeat} 请求体
 * {@code {"schemaVersion":1,"sdkVersion":"x.y.z","heartbeat":{...}}}。
 *
 * <p>F2 字段清单中的 sdkVersion 由信封层承载（与 {@link EventEnvelope} 同一约定），
 * 心跳负载 {@link InstanceHeartbeat} 不再重复。
 */
public class HeartbeatEnvelope {

    private int schemaVersion = EventEnvelope.SCHEMA_VERSION;

    /** starter 构件版本。 */
    private String sdkVersion;

    private InstanceHeartbeat heartbeat;

    public HeartbeatEnvelope() {
    }

    /** 以当前主版本构造信封。 */
    public static HeartbeatEnvelope of(String sdkVersion, InstanceHeartbeat heartbeat) {
        HeartbeatEnvelope envelope = new HeartbeatEnvelope();
        envelope.sdkVersion = sdkVersion;
        envelope.heartbeat = heartbeat;
        return envelope;
    }

    /** 校验信封完整性，返回全部违规描述；空列表＝合法。 */
    public List<String> validate() {
        List<String> violations = new ArrayList<String>();
        if (schemaVersion != EventEnvelope.SCHEMA_VERSION) {
            violations.add("schemaVersion 不受支持：期望 " + EventEnvelope.SCHEMA_VERSION + "，实际 " + schemaVersion);
        }
        if (sdkVersion == null || sdkVersion.trim().isEmpty()) {
            violations.add("sdkVersion 缺失");
        }
        if (heartbeat == null) {
            violations.add("heartbeat 缺失");
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

    public InstanceHeartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(InstanceHeartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }
}
