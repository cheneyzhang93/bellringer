package io.github.cheneyzhang93.bellringer.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例心跳（F2 冻结）——{@link HeartbeatEnvelope} 的 heartbeat 负载。
 *
 * <p>默认 30s 一拍；控制台 90s（3 拍）未收到判失联。发送失败静默丢弃、下一拍补报。
 * {@code startedAt} 为应用实例启动时刻（epoch millis），非心跳发送时刻，必须显式设置。
 */
public class InstanceHeartbeat {

    /** 统一应用标识（F5 取值链）。 */
    private String app;

    /** 实例标识 hostname:pid，见 {@link InstanceIds}。 */
    private String instance;

    /** 应用自身版本号，可空。 */
    private String version;

    /** 环境标识，可空。 */
    private String env;

    /** 应用实例启动时刻（epoch millis）。 */
    private long startedAt;

    /** 模块开关快照（模块名 → 是否启用），可空。 */
    private Map<String, Boolean> modules;

    public InstanceHeartbeat() {
    }

    /**
     * 校验契约约束，返回全部违规描述；空列表＝合法。
     *
     * <p>必填：app / instance（须符合 hostname:pid）/ startedAt；version / env / modules 可选。
     */
    public List<String> validate() {
        List<String> violations = new ArrayList<String>();
        if (isBlank(app)) {
            violations.add("app 缺失");
        }
        if (isBlank(instance)) {
            violations.add("instance 缺失（hostname:pid）");
        } else if (!InstanceIds.isValid(instance)) {
            violations.add("instance 需为 hostname:pid：" + instance);
        }
        if (startedAt <= 0L) {
            violations.add("startedAt 缺失或非法（需为 epoch millis，应用启动时刻）");
        }
        return violations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Map<String, Boolean> getModules() {
        return modules;
    }

    public void setModules(Map<String, Boolean> modules) {
        this.modules = modules == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(modules));
    }

    @Override
    public String toString() {
        return "InstanceHeartbeat{app=" + app + ", instance=" + instance + ", version=" + version + ", env=" + env
                + ", startedAt=" + startedAt + "}";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 构造器：startedAt 无缺省值（应用启动时刻由调用方显式提供，避免误用发送时刻）。 */
    public static final class Builder {

        private String app;
        private String instance;
        private String version;
        private String env;
        private Long startedAt;
        private Map<String, Boolean> modules;

        public Builder app(String app) {
            this.app = app;
            return this;
        }

        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder env(String env) {
            this.env = env;
            return this;
        }

        public Builder startedAt(long startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder modules(Map<String, Boolean> modules) {
            this.modules = modules;
            return this;
        }

        public InstanceHeartbeat build() {
            InstanceHeartbeat heartbeat = new InstanceHeartbeat();
            heartbeat.app = app;
            heartbeat.instance = instance;
            heartbeat.version = version;
            heartbeat.env = env;
            heartbeat.startedAt = startedAt != null ? startedAt.longValue() : 0L;
            heartbeat.setModules(modules);
            return heartbeat;
        }
    }
}
