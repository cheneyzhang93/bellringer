package io.github.cheneyzhang93.bellringer.starter.trace;

/**
 * MDC 键约定（§2 链路，字段名对齐 OTel 语义约定，随结构化日志进入检索系统形成索引字段）。
 */
public final class MdcKeys {

    /** W3C trace-id（32 位小写 hex）。 */
    public static final String TRACE_ID = "trace_id";

    /** W3C span-id（16 位小写 hex）。 */
    public static final String SPAN_ID = "span_id";

    /** 业务用户标识（对齐 OTel user.id）。 */
    public static final String USER_ID = "user_id";

    private MdcKeys() {
    }
}
