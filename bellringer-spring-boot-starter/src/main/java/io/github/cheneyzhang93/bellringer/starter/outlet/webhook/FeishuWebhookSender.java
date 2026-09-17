package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 飞书群机器人出口（OSS 通道）：互动卡片（lark_md 渲染 markdown 文本）+ 可选签名头。
 *
 * <p>签名：{@code sign = Base64(HmacSHA256(key = timestamp + "\n" + secret, data = ""))}，
 * 未配置 secret 时不带 timestamp/sign。成功判定：HTTP 200 且 code=0（兼容旧 StatusCode=0）。
 */
public class FeishuWebhookSender implements AlertSender {

    public static final String NAME = "feishu";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Pattern ZERO_CODE = Pattern.compile("\"code\"\\s*:\\s*0\\s*[,}]");

    private static final Pattern ZERO_STATUS_CODE = Pattern.compile("\"StatusCode\"\\s*:\\s*0\\s*[,}]");

    private final BellringerProperties.Alert.Feishu config;

    private final int connectMillis;

    private final int readMillis;

    public FeishuWebhookSender(BellringerProperties.Alert.Feishu config, int connectMillis, int readMillis) {
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
        String title = event.getTitle() == null || event.getTitle().isEmpty() ? event.getType() : event.getTitle();
        String textObject = JsonText.object("tag", "lark_md", "content", markdown);
        String element = JsonText.object("tag", "div", "text", JsonText.raw(textObject));
        String card = JsonText.object(
                "config", JsonText.raw(JsonText.object("wide_screen_mode", Boolean.TRUE)),
                "header", JsonText.raw(JsonText.object(
                        "title", JsonText.raw(JsonText.object("tag", "plain_text", "content", title)))),
                "elements", JsonText.raw(JsonText.array(JsonText.raw(element))));
        String body;
        if (config.getSecret() == null || config.getSecret().trim().isEmpty()) {
            body = JsonText.object("msg_type", "interactive", "card", JsonText.raw(card));
        } else {
            long timestamp = System.currentTimeMillis();
            body = JsonText.object(
                    "timestamp", String.valueOf(timestamp),
                    "sign", feishuSign(timestamp, config.getSecret()),
                    "msg_type", "interactive",
                    "card", JsonText.raw(card));
        }
        WebhookClient.Response response = WebhookClient.postJson(config.getWebhook(), body, connectMillis, readMillis);
        boolean ok = response.getStatus() == 200
                && (ZERO_CODE.matcher(response.getBody()).find()
                || ZERO_STATUS_CODE.matcher(response.getBody()).find());
        if (!ok) {
            throw new IOException("飞书发送失败 status=" + response.getStatus() + " body=" + response.getBody());
        }
    }

    static String feishuSign(long timestamp, String secret) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((timestamp + "\n" + secret).getBytes(UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
        } catch (Exception e) {
            throw new IOException("飞书加签失败: " + e.getMessage(), e);
        }
    }
}
