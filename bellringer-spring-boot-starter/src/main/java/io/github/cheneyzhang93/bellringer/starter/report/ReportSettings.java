package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;

/**
 * 上报出口生效配置（F1：mode 含 http 时 endpoint/token 必填——显式配置错误启动即失败）。
 */
public final class ReportSettings {

    private final boolean httpEnabled;

    private final String endpoint;

    private final String token;

    private final long timeoutMillis;

    private final int queueSize;

    private final int maxRetries;

    private final int heartbeatIntervalSeconds;

    private ReportSettings(BellringerProperties properties) {
        BellringerProperties.Report report = properties.getReport();
        this.httpEnabled = report.getMode() == BellringerProperties.Report.Mode.HTTP
                || report.getMode() == BellringerProperties.Report.Mode.BOTH;
        this.endpoint = trimTrailingSlash(report.getEndpoint());
        this.token = report.getToken();
        this.timeoutMillis = report.getTimeoutMillis();
        this.queueSize = report.getQueueSize();
        this.maxRetries = report.getMaxRetries();
        this.heartbeatIntervalSeconds = report.getHeartbeatIntervalSeconds();
        if (httpEnabled && endpoint.isEmpty()) {
            throw new IllegalStateException("敲钟人：observability.report.mode=" + report.getMode()
                    + " 但 endpoint 未配置（请填写控制台地址，或改回 mode=webhook）");
        }
        if (httpEnabled && token.trim().isEmpty()) {
            throw new IllegalStateException("敲钟人：observability.report.mode=" + report.getMode()
                    + " 但 token 未配置（请填写控制台签发的租户 Token，或改回 mode=webhook）");
        }
    }

    public static ReportSettings from(BellringerProperties properties) {
        return new ReportSettings(properties);
    }

    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getToken() {
        return token;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
