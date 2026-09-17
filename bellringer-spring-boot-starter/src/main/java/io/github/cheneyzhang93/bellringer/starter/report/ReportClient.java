package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.ApiPaths;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.LocalAlertPusher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 上报 HTTP 通道（F1）：Bearer 认证、单次超时 3s、不跟随重定向；401 → 停止上报并本地告警一次。
 *
 * <p>本类只负责一次请求与状态判定，退避重试由 {@link HttpEventReporter} 承担。
 */
public class ReportClient {

    /** 未发送（通道已停止）。 */
    public static final int NOT_SENT = -1;

    private static final Logger log = LoggerFactory.getLogger(ReportClient.class);

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final ReportSettings settings;

    private final ReportState state;

    private final ObjectProvider<LocalAlertPusher> localPusher;

    private final AppIdentity identity;

    public ReportClient(ReportSettings settings, ReportState state,
                        ObjectProvider<LocalAlertPusher> localPusher, AppIdentity identity) {
        this.settings = settings;
        this.state = state;
        this.localPusher = localPusher;
        this.identity = identity;
    }

    /** 发送一次（阻塞 ≤ 连接超时 + 读取超时）；返回 HTTP 状态码，通道已停止时返回 {@link #NOT_SENT}。 */
    public int post(String path, String json) throws IOException {
        if (state.isStopped()) {
            return NOT_SENT;
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(settings.getEndpoint() + path).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout((int) settings.getTimeoutMillis());
            conn.setReadTimeout((int) settings.getTimeoutMillis());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty(ApiPaths.AUTHORIZATION_HEADER, ApiPaths.BEARER_PREFIX + settings.getToken());
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(json.getBytes(UTF_8));
            } finally {
                out.close();
            }
            int status = conn.getResponseCode();
            drain(conn, status);
            if (status == 401) {
                onTokenInvalid(status);
            }
            return status;
        } finally {
            conn.disconnect();
        }
    }

    /** F1：401 后停止上报（事件 + 心跳）并本地告警一次；Token 无效属配置事故，必须让人看见。 */
    private void onTokenInvalid(int status) {
        if (!state.stop("控制台返回 " + status + "（Token 无效）")) {
            return; // 已在其他线程完成停止与告警
        }
        log.error("[上报出口] 收到 {}，停止事件上报与心跳（F1）。请在控制台轮换 Token 后重启实例。", status);
        LocalAlertPusher pusher = localPusher.getIfAvailable();
        if (pusher == null) {
            return;
        }
        try {
            pusher.pushLocal(AlertEvent.builder()
                    .type(EventTypes.SYSTEM_ERROR)
                    .level(AlertLevel.P1)
                    .title("上报通道 Token 无效，已停止上报")
                    .message("控制台返回 401。事件与心跳上报已停止；请在控制台轮换 Token 后重启应用。")
                    .source("io.github.cheneyzhang93.bellringer.report")
                    .aggregateKey("report-token-invalid")
                    .app(identity.getApp())
                    .instance(identity.getInstance())
                    .env(identity.getEnv())
                    .build());
        } catch (Exception e) {
            log.error("[上报出口] Token 失效本地告警投递失败: {}", e.toString());
        }
    }

    private static void drain(HttpURLConnection conn, int status) {
        InputStream stream = null;
        try {
            stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return;
            }
            byte[] buffer = new byte[512];
            while (stream.read(buffer) != -1) {
                // 消费响应体，立即结束
            }
        } catch (IOException ignored) {
            // 响应体读取失败不影响状态判定
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // 忽略关闭异常
                }
            }
        }
    }
}
