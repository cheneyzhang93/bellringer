package io.github.cheneyzhang93.bellringer.starter.report;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 上报出口（事件 + 心跳）生效条件：{@code observability.report.mode} 含 http（http / both，大小写不敏感）。
 */
final class OnReportHttpCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("observability.report.mode", "webhook");
        String normalized = mode.trim();
        return "http".equalsIgnoreCase(normalized) || "both".equalsIgnoreCase(normalized);
    }
}
