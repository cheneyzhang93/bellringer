package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 心跳 modules 快照（F2 字段）：本实例启用了哪些事件源与出口，控制台据此解释"为什么没收到某类事件"。
 */
public final class ModuleFlags {

    private ModuleFlags() {
    }

    public static Map<String, Boolean> of(BellringerProperties properties) {
        Map<String, Boolean> flags = new LinkedHashMap<String, Boolean>();
        flags.put("report", properties.getReport().getMode() != BellringerProperties.Report.Mode.WEBHOOK);
        flags.put("alert-dingtalk", properties.getAlert().getDingtalk().isEnabled());
        flags.put("alert-wecom", properties.getAlert().getWecom().isEnabled());
        flags.put("alert-feishu", properties.getAlert().getFeishu().isEnabled());
        flags.put("source-error-log", properties.getSources().getErrorLog().isEnabled());
        flags.put("source-slow-sql", properties.getSlowSql().isEnabled());
        flags.put("source-lock-timeout", properties.getSources().getLockTimeout().isEnabled());
        flags.put("source-scheduled-task", properties.getSources().getScheduledTask().isEnabled());
        flags.put("source-health-check", properties.getSources().getHealthCheck().isEnabled());
        flags.put("source-access-log", properties.getSources().getAccessLog().isEnabled());
        return flags;
    }
}
