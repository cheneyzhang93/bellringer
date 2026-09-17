package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * 群机器人 webhook POST 客户端（J8 {@link HttpURLConnection}，零额外依赖）。
 *
 * <p>固定 3xx 不跟随跳转（webhook 地址不应重定向，避免凭据泄露到非预期主机）；
 * 读取响应体用于成功判定与失败留痕（截断由调用方处理）。
 */
public final class WebhookClient {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final int MAX_BODY_CHARS = 1000;

    private WebhookClient() {
    }

    /** 单次 POST 结果（status + 截断后的响应体）。 */
    public static final class Response {

        private final int status;

        private final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }

    public static Response postJson(String url, String jsonBody, int connectMillis, int readMillis)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(connectMillis);
            conn.setReadTimeout(readMillis);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(jsonBody.getBytes(UTF_8));
            } finally {
                out.close();
            }
            int status = conn.getResponseCode();
            return new Response(status, readBody(conn, status));
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn, int status) {
        InputStream stream = null;
        try {
            stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            String body = new String(buffer.toByteArray(), UTF_8);
            if (body.length() > MAX_BODY_CHARS) {
                return body.substring(0, MAX_BODY_CHARS) + "...";
            }
            return body;
        } catch (IOException e) {
            return "";
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // 读取失败不影响成功/失败判定
                }
            }
        }
    }
}
