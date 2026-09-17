package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 钉钉群机器人出口（OSS 通道）：markdown 消息 + 加签 + 分级 @。
 *
 * <p>加签：{@code sign = URLEncode(Base64(HmacSHA256(secret, timestamp + "\n" + secret)))}；
 * 分级 @：P0 按配置 @所有人，P1 @ 配置手机号列表，P2 不 @。成功判定：HTTP 200 且 errcode=0。
 */
public class DingTalkWebhookSender implements AlertSender {

    public static final String NAME = "dingtalk";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Pattern ZERO_ERRCODE = Pattern.compile("\"errcode\"\\s*:\\s*0\\s*[,}]");

    private final BellringerProperties.Alert.DingTalk config;

    private final int connectMillis;

    private final int readMillis;

    public DingTalkWebhookSender(BellringerProperties.Alert.DingTalk config, int connectMillis, int readMillis) {
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
        String body = JsonText.object(
                "msgtype", "markdown",
                "markdown", JsonText.raw(JsonText.object("title", title, "text", markdown)),
                "at", JsonText.raw(atJson(event.getLevel())));
        WebhookClient.Response response = WebhookClient.postJson(signedUrl(), body, connectMillis, readMillis);
        if (response.getStatus() != 200 || !ZERO_ERRCODE.matcher(response.getBody()).find()) {
            throw new IOException("钉钉发送失败 status=" + response.getStatus() + " body=" + response.getBody());
        }
    }

    /** 加签 URL（secret 为空＝旧版关键字机器人，原样返回 webhook）。 */
    String signedUrl() throws IOException {
        if (config.getSecret() == null || config.getSecret().trim().isEmpty()) {
            return config.getWebhook();
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + config.getSecret();
        String sign = URLEncoder.encode(base64HmacSha256(stringToSign, config.getSecret()), "UTF-8");
        return config.getWebhook() + (config.getWebhook().contains("?") ? "&" : "?")
                + "timestamp=" + timestamp + "&sign=" + sign;
    }

    private String atJson(AlertLevel level) {
        List<String> atMobiles = config.getAtMobiles();
        if (level == AlertLevel.P0 && config.isAtAll()) {
            return JsonText.object("isAtAll", Boolean.TRUE);
        }
        if (level == AlertLevel.P1 && atMobiles != null && !atMobiles.isEmpty()) {
            return JsonText.object("atMobiles", JsonText.raw(JsonText.array(atMobiles.toArray())));
        }
        return JsonText.object("isAtAll", Boolean.FALSE);
    }

    private static String base64HmacSha256(String data, String secret) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new IOException("钉钉加签失败: " + e.getMessage(), e);
        }
    }
}
