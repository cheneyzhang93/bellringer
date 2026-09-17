package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.testing.StubHttpServer;
import io.github.cheneyzhang93.bellringer.starter.testing.TestEvents;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 三家群机器人出口验收：报文形状、分级 @、加签算法、errcode/成功判定与失败抛错。
 */
class WebhookSendersTest {

    private static final Pattern TIMESTAMP = Pattern.compile("timestamp=(\\d+)");

    private static final Pattern BODY_TIMESTAMP = Pattern.compile("\"timestamp\":\"(\\d+)\"");

    private StubHttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void dingTalkP0MentionsAllByDefault() throws IOException {
        BellringerProperties.Alert.DingTalk config = new BellringerProperties.Alert.DingTalk();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/robot/send?access_token=tk");
        DingTalkWebhookSender sender = new DingTalkWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.sample("demo").level(AlertLevel.P0).build(), "### [P0] 标题\n正文");

        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.method).isEqualTo("POST");
        assertThat(request.path).isEqualTo("/robot/send");
        assertThat(request.query).isEqualTo("access_token=tk");
        assertThat(request.contentType).contains("application/json");
        assertThat(request.body).contains("\"msgtype\":\"markdown\"");
        assertThat(request.body).contains("\"isAtAll\":true");
        assertThat(request.body).contains("### [P0] 标题");
    }

    @Test
    void dingTalkP1MentionsConfiguredMobiles() throws IOException {
        BellringerProperties.Alert.DingTalk config = new BellringerProperties.Alert.DingTalk();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/robot/send");
        config.setAtAll(false);
        config.setAtMobiles(Arrays.asList("13800000000", "13900000000"));
        DingTalkWebhookSender sender = new DingTalkWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.sample("demo").level(AlertLevel.P1).build(), "正文");

        assertThat(server.lastRequest().body)
                .contains("\"atMobiles\":[\"13800000000\",\"13900000000\"]");
    }

    @Test
    void dingTalkSignMatchesFrozenAlgorithm() throws Exception {
        String secret = "SEC0123456789";
        BellringerProperties.Alert.DingTalk config = new BellringerProperties.Alert.DingTalk();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/robot/send?access_token=tk");
        config.setSecret(secret);
        DingTalkWebhookSender sender = new DingTalkWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.valid("demo"), "正文");

        String rawQuery = server.lastRequest().rawQuery;
        Matcher matcher = TIMESTAMP.matcher(rawQuery);
        assertThat(matcher.find()).isTrue();
        String timestamp = matcher.group(1);
        String expectSign = java.net.URLEncoder.encode(hmacBase64(secret, timestamp + "\n" + secret), "UTF-8");
        assertThat(rawQuery).contains("timestamp=" + timestamp).contains("sign=" + expectSign);
    }

    @Test
    void dingTalkBusinessFailureThrows() {
        BellringerProperties.Alert.DingTalk config = new BellringerProperties.Alert.DingTalk();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/robot/send");
        DingTalkWebhookSender sender = new DingTalkWebhookSender(config, 3000, 5000);
        server.enqueue(200, "{\"errcode\":310000,\"errmsg\":\"sign not match\"}");

        assertThatThrownBy(() -> sender.send(TestEvents.valid("demo"), "正文"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("310000");
    }

    @Test
    void weComPostsMarkdownContent() throws IOException {
        BellringerProperties.Alert.WeCom config = new BellringerProperties.Alert.WeCom();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/cgi-bin/webhook/send?key=k");
        WeComWebhookSender sender = new WeComWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.valid("demo"), "### [P1] 标题\n正文");

        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.path).isEqualTo("/cgi-bin/webhook/send");
        assertThat(request.body).isEqualTo(
                "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"### [P1] 标题\\n正文\"}}");
    }

    @Test
    void weComBusinessFailureThrows() {
        BellringerProperties.Alert.WeCom config = new BellringerProperties.Alert.WeCom();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/cgi-bin/webhook/send?key=k");
        WeComWebhookSender sender = new WeComWebhookSender(config, 3000, 5000);
        server.enqueue(200, "{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");

        assertThatThrownBy(() -> sender.send(TestEvents.valid("demo"), "正文"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("93000");
    }

    @Test
    void feishuPostsInteractiveCardAndAcceptsBothSuccessShapes() throws IOException {
        BellringerProperties.Alert.Feishu config = new BellringerProperties.Alert.Feishu();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/open-apis/bot/v2/hook/h");
        FeishuWebhookSender sender = new FeishuWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.sample("demo").title("标题").build(), "正文");

        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.body).contains("\"msg_type\":\"interactive\"");
        assertThat(request.body).contains("\"tag\":\"lark_md\"");
        assertThat(request.body).contains("\"content\":\"正文\"");
        assertThat(request.body).contains("\"plain_text\"");

        server.enqueue(200, "{\"StatusCode\":0,\"StatusMessage\":\"success\"}");
        sender.send(TestEvents.valid("demo"), "正文");
    }

    @Test
    void feishuSignIsCarriedInBodyPerProtocol() throws Exception {
        String secret = "FeishuSecret";
        BellringerProperties.Alert.Feishu config = new BellringerProperties.Alert.Feishu();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/hook/h");
        config.setSecret(secret);
        FeishuWebhookSender sender = new FeishuWebhookSender(config, 3000, 5000);

        sender.send(TestEvents.valid("demo"), "正文");

        StubHttpServer.Recorded request = server.lastRequest();
        assertThat(request.query).isNull();
        Matcher matcher = BODY_TIMESTAMP.matcher(request.body);
        assertThat(matcher.find()).isTrue();
        String timestamp = matcher.group(1);
        // 冻结算法：Base64(HmacSHA256(key = timestamp + "\n" + secret, data = ""))，随 body 明文传输
        assertThat(request.body).contains("\"sign\":\"" + hmacBase64(timestamp + "\n" + secret, "") + "\"");
    }

    @Test
    void feishuBusinessFailureThrows() {
        BellringerProperties.Alert.Feishu config = new BellringerProperties.Alert.Feishu();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/hook/h");
        FeishuWebhookSender sender = new FeishuWebhookSender(config, 3000, 5000);
        server.enqueue(200, "{\"code\":19021,\"msg\":\"sign match fail\"}");

        assertThatThrownBy(() -> sender.send(TestEvents.valid("demo"), "正文"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("19021");
    }

    @Test
    void non200StatusThrowsForAllChannels() {
        server.enqueue(500, "boom");
        BellringerProperties.Alert.WeCom config = new BellringerProperties.Alert.WeCom();
        config.setEnabled(true);
        config.setWebhook(server.url() + "/hook");
        WeComWebhookSender sender = new WeComWebhookSender(config, 3000, 5000);
        AlertEvent event = TestEvents.valid("demo");

        assertThatThrownBy(() -> sender.send(event, "正文")).isInstanceOf(IOException.class);
    }

    private static String hmacBase64(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes("UTF-8")));
    }
}
