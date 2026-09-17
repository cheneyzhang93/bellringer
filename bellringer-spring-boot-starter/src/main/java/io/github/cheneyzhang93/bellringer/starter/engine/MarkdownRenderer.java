package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 告警 markdown 渲染（IM 通道统一模板）：标题/时间/环境/实例/级别/来源/trace_id/检索/详情。
 *
 * <p>模板字段固定，便于单测断言；trace 检索链接与排障指引由 {@code observability.alert.trace-*}
 * 配置，为空则整行不输出（无集中检索环境下的兜底通道）。
 */
public class MarkdownRenderer {

    static final int MAX_MESSAGE_CHARS = 4000;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BellringerProperties properties;

    private final AppIdentity identity;

    public MarkdownRenderer(BellringerProperties properties, AppIdentity identity) {
        this.properties = properties;
        this.identity = identity;
    }

    public String render(AlertEvent event) {
        StringBuilder sb = new StringBuilder(512);
        AlertLevel level = event.getLevel() == null ? AlertLevel.P2 : event.getLevel();
        String title = event.getTitle() == null || event.getTitle().isEmpty() ? event.getType() : event.getTitle();
        sb.append("### [").append(level.name()).append("] ").append(title).append('\n');
        sb.append("- **时间**：").append(TIME_FORMAT.format(Instant.ofEpochMilli(event.getOccurredAt()))).append('\n');
        sb.append("- **环境**：").append(identity.getEnv()).append('\n');
        sb.append("- **实例**：").append(identity.getInstance()).append('\n');
        sb.append("- **级别**：").append(level.name()).append('(').append(levelLabel(level)).append(")\n");
        if (event.getSource() != null && !event.getSource().isEmpty()) {
            sb.append("- **来源**：").append(event.getSource()).append('\n');
        }
        if (event.getTraceId() != null && !event.getTraceId().isEmpty()) {
            sb.append("- **trace_id**：`").append(event.getTraceId()).append("`\n");
            String urlTemplate = properties.getAlert().getTraceUrlTemplate();
            if (!urlTemplate.isEmpty()) {
                sb.append("- **检索**：").append(urlTemplate.replace("{traceId}", event.getTraceId())).append('\n');
            }
            String hint = properties.getAlert().getTraceQueryHint();
            if (!hint.isEmpty()) {
                sb.append("- **排障指引**：\n").append(hint.replace("{traceId}", event.getTraceId())).append('\n');
            }
        }
        if (event.getMessage() != null && !event.getMessage().isEmpty()) {
            sb.append("- **详情**：\n").append(truncate(event.getMessage()));
        }
        return sb.toString();
    }

    private static String truncate(String message) {
        if (message.length() <= MAX_MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_CHARS) + "\n...(已截断)";
    }

    private static String levelLabel(AlertLevel level) {
        switch (level) {
            case P0:
                return "致命";
            case P1:
                return "严重";
            default:
                return "一般";
        }
    }
}
