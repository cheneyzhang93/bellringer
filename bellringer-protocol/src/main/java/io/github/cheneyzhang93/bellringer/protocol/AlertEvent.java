package io.github.cheneyzhang93.bellringer.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 告警事件 v1 —— starter 与控制台之间的冻结数据契约（F4：只加不改不删）。
 *
 * <p>传输与消费约定：
 * <ul>
 *   <li>{@code occurredAt} 传输格式为 epoch millis；控制台接收后另存 receivedAt。</li>
 *   <li>可选字段为 null 时序列化省略；消费端必须容忍缺失字段与未知字段
 *       （Spring Boot 默认 ObjectMapper 已关闭 FAIL_ON_UNKNOWN_PROPERTIES）。</li>
 *   <li>本类零注解、零框架依赖，两侧直接用默认 Jackson 映射；生产侧建议走 {@link #builder()}
 *       补齐 level / occurredAt / eventId 缺省值，接收侧反序列化后用 {@link #validate()} 校验。</li>
 * </ul>
 */
public class AlertEvent {

    /** type 约束：kebab-case（小写字母/数字、短横线分段），兼作去重键前缀。 */
    public static final String TYPE_PATTERN = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$";

    /** type 最大长度（控制台存储列宽以此为准）。 */
    public static final int MAX_TYPE_LENGTH = 64;

    /** traceId 约束：W3C trace-id，32 位小写 hex。 */
    public static final String TRACE_ID_PATTERN = "^[0-9a-f]{32}$";

    private static final Pattern TYPE_REGEX = Pattern.compile(TYPE_PATTERN);
    private static final Pattern TRACE_ID_REGEX = Pattern.compile(TRACE_ID_PATTERN);

    /** 事件类型，kebab-case，见 {@link EventTypes}；兼作去重键前缀。 */
    private String type;

    /** 级别，缺省 P2。 */
    private AlertLevel level = AlertLevel.P2;

    /** 通知标题。 */
    private String title;

    /** 明细内容，允许 markdown。 */
    private String message;

    /** 定位信息：类.方法 / SQL 指纹。 */
    private String source;

    /** W3C trace-id（32 位小写 hex），可空。 */
    private String traceId;

    /** 聚合去重键：null＝逐条；非 null＝控制台按 app+type+aggregateKey 在窗口内聚合。 */
    private String aggregateKey;

    /** 客户端发生时刻（epoch millis），时钟以客户端为准。 */
    private long occurredAt;

    /** 统一应用标识（F5 取值链：observability.app → spring.application.name → 启动失败）。 */
    private String app;

    /** 实例标识 hostname:pid，见 {@link InstanceIds}。 */
    private String instance;

    /** 环境标识（如 prod / gray / dev），可空。 */
    private String env;

    /** 事件唯一标识（UUID），重传幂等键。 */
    private String eventId;

    /** 扩展位：OSS 不解析、原样透传；值需为 JSON 可序列化类型。 */
    private Map<String, Object> payload;

    public AlertEvent() {
    }

    /**
     * 校验契约约束，返回全部违规描述；空列表＝合法。
     *
     * <p>必填：type / level / title / app / eventId / occurredAt；
     * 可选字段缺失不算违规，但提供时必须符合格式（traceId / instance / eventId）。
     */
    public List<String> validate() {
        List<String> violations = new ArrayList<String>();
        if (isBlank(type)) {
            violations.add("type 缺失");
        } else if (type.length() > MAX_TYPE_LENGTH || !TYPE_REGEX.matcher(type).matches()) {
            violations.add("type 非 kebab-case 或超长（最大 " + MAX_TYPE_LENGTH + "）：" + type);
        }
        if (level == null) {
            violations.add("level 缺失（P0/P1/P2）");
        }
        if (isBlank(title)) {
            violations.add("title 缺失");
        }
        if (isBlank(app)) {
            violations.add("app 缺失");
        }
        if (isBlank(eventId)) {
            violations.add("eventId 缺失（UUID）");
        } else if (!isUuid(eventId)) {
            violations.add("eventId 非合法 UUID：" + eventId);
        }
        if (occurredAt <= 0L) {
            violations.add("occurredAt 缺失或非法（需为 epoch millis）");
        }
        if (!isBlank(traceId) && !TRACE_ID_REGEX.matcher(traceId).matches()) {
            violations.add("traceId 需为 32 位小写 hex：" + traceId);
        }
        if (!isBlank(instance) && !InstanceIds.isValid(instance)) {
            violations.add("instance 需为 hostname:pid：" + instance);
        }
        return violations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public void setLevel(AlertLevel level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
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

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(payload));
    }

    @Override
    public String toString() {
        return "AlertEvent{type=" + type + ", level=" + level + ", title=" + title + ", app=" + app
                + ", instance=" + instance + ", env=" + env + ", eventId=" + eventId + ", occurredAt=" + occurredAt
                + "}";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 构造器：补齐 level（P2）、occurredAt（当前时刻）、eventId（随机 UUID）缺省值。 */
    public static final class Builder {

        private String type;
        private AlertLevel level = AlertLevel.P2;
        private String title;
        private String message;
        private String source;
        private String traceId;
        private String aggregateKey;
        private Long occurredAt;
        private String app;
        private String instance;
        private String env;
        private String eventId;
        private Map<String, Object> payload;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder level(AlertLevel level) {
            this.level = level;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder aggregateKey(String aggregateKey) {
            this.aggregateKey = aggregateKey;
            return this;
        }

        public Builder occurredAt(long occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder app(String app) {
            this.app = app;
            return this;
        }

        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }

        public Builder env(String env) {
            this.env = env;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public AlertEvent build() {
            AlertEvent event = new AlertEvent();
            event.type = type;
            event.level = level;
            event.title = title;
            event.message = message;
            event.source = source;
            event.traceId = traceId;
            event.aggregateKey = aggregateKey;
            event.occurredAt = occurredAt != null ? occurredAt.longValue() : System.currentTimeMillis();
            event.app = app;
            event.instance = instance;
            event.env = env;
            event.eventId = isBlank(eventId) ? UUID.randomUUID().toString() : eventId;
            event.setPayload(payload);
            return event;
        }
    }
}
