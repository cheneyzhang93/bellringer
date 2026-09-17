package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 企业微信群机器人出口（OSS 通道）：markdown 消息（markdown 内联 trace_id/详情）。
 *
 * <p>成功判定：HTTP 200 且 errcode=0；企业微信 markdown 不支持 @ 语法，分级仅在消息内体现。
 */
public class WeComWebhookSender implements AlertSender {

    public static final String NAME = "wecom";

    private static final Pattern ZERO_ERRCODE = Pattern.compile("\"errcode\"\\s*:\\s*0\\s*[,}]");

    private final BellringerProperties.Alert.WeCom config;

    private final int connectMillis;

    private final int readMillis;

    public WeComWebhookSender(BellringerProperties.Alert.WeCom config, int connectMillis, int readMillis) {
        this.config = config;
        this.connectMillis = connectMillis;
        this.readMillis = readMillis;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void send(AlertEvent event, String markdown) throws IOException {
        String body = JsonText.object(
                "msgtype", "markdown",
                "markdown", JsonText.raw(JsonText.object("content", markdown)));
        WebhookClient.Response response = WebhookClient.postJson(config.getWebhook(), body, connectMillis, readMillis);
        if (response.getStatus() != 200 || !ZERO_ERRCODE.matcher(response.getBody()).find()) {
            throw new IOException("企微发送失败 status=" + response.getStatus() + " body=" + response.getBody());
        }
    }
}
