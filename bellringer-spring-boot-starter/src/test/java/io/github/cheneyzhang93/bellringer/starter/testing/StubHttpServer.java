package io.github.cheneyzhang93.bellringer.starter.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 测试桩 HTTP 服务（JDK 内置 HttpServer）：记录全部请求，按 FIFO 返回预置响应，
 * 预置耗尽后回落到 200 + 三家通用成功体。
 */
public final class StubHttpServer {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** 同时满足钉钉/企微（errcode=0）与飞书（code=0 / StatusCode=0）的成功体。 */
    private static final String DEFAULT_BODY = "{\"errcode\":0,\"code\":0,\"StatusCode\":0}";

    public static final class Recorded {

        public final String method;

        public final String path;

        /** 解码后的查询串（URI.getQuery()）。 */
        public final String query;

        /** 原始查询串（URI.getRawQuery()，保留 percent-encoding，用于加签断言）。 */
        public final String rawQuery;

        public final String authorization;

        public final String contentType;

        public final String body;

        Recorded(HttpExchange exchange, String body) {
            this.method = exchange.getRequestMethod();
            this.path = exchange.getRequestURI().getPath();
            this.query = exchange.getRequestURI().getQuery();
            this.rawQuery = exchange.getRequestURI().getRawQuery();
            this.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            this.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            this.body = body;
        }
    }

    private static final class Canned {

        final int status;

        final String body;

        final long delayMillis;

        Canned(int status, String body, long delayMillis) {
            this.status = status;
            this.body = body;
            this.delayMillis = delayMillis;
        }
    }

    private final HttpServer server;

    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<Recorded>());

    private final Queue<Canned> canned = new LinkedList<Canned>();

    public StubHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    /** 预置一次响应（FIFO）。 */
    public synchronized void enqueue(int status, String body) {
        canned.add(new Canned(status, body, 0L));
    }

    /** 预置一次带延迟的响应（模拟服务端慢响应，用于有界队列丢弃验证）。 */
    public synchronized void enqueue(int status, String body, long delayMillis) {
        canned.add(new Canned(status, body, delayMillis));
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requests.size();
    }

    public List<Recorded> requests() {
        synchronized (requests) {
            return new ArrayList<Recorded>(requests);
        }
    }

    public Recorded lastRequest() {
        synchronized (requests) {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }
    }

    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = read(exchange.getRequestBody());
        requests.add(new Recorded(exchange, body));
        Canned response;
        synchronized (this) {
            response = canned.poll();
        }
        if (response == null) {
            response = new Canned(200, DEFAULT_BODY, 0L);
        }
        if (response.delayMillis > 0) {
            try {
                Thread.sleep(response.delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] payload = response.body.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(response.status, payload.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(payload);
        } finally {
            out.close();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        in.close();
        return new String(buffer.toByteArray(), UTF_8);
    }
}
