package io.github.cheneyzhang93.bellringer.starter.source.access;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.trace.MdcKeys;
import java.io.IOException;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 访问日志事件源（默认关闭）：每请求一条 INFO 日志，另按响应结果发事件。
 *
 * <p>事件口径：5xx＝P1（{@code emit-server-errors} 控制）；非 5xx 且超 {@code slow-millis}＝P2；
 * 二者不叠加（5xx 优先）。聚合键＝{@code 方向|方法|归一化路径}——路径中的数字/UUID 段归一为 {@code {id}}，
 * 同一接口的重复问题窗口内只推首条。
 *
 * <p>只记录方法/路径/状态/耗时（绝不采集请求与响应体）；本过滤器排在业务过滤器链外层
 * （{@link #ORDER}），据此拿到最终状态码，异常路径同样在 finally 中留痕。
 */
public class AccessLogFilter extends OncePerRequestFilter implements Ordered {

    /** 置于最高优先级之后 1000 位：包住绝大多数业务过滤器，拿到的状态码最接近最终值。 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private static final Pattern TRACE_ID = Pattern.compile(AlertEvent.TRACE_ID_PATTERN);

    private final BellringerProperties.Sources.AccessLog config;

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    public AccessLogFilter(BellringerProperties.Sources.AccessLog config,
                           AlertEventFactory events, AlertEventSink sink) {
        this.config = config;
        this.events = events;
        this.sink = sink;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = traceId();
        try {
            chain.doFilter(request, response);
        } finally {
            record(request, response, traceId, (System.nanoTime() - startNanos) / 1_000_000L);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, String traceId, long millis) {
        try {
            String method = request.getMethod();
            String path = AccessPath.normalize(request.getRequestURI());
            int status = response.getStatus();
            log.info("[访问日志] {} {} -> {} {}ms{}", method, path, status, millis,
                    traceId == null ? "" : " trace=" + traceId);
            if (status >= 500) {
                if (config.isEmitServerErrors()) {
                    emit(AlertLevel.P1, "接口 5xx：" + method + " " + path,
                            "server-error|" + method + "|" + path, method, path, status, millis, traceId);
                }
            } else if (millis >= config.getSlowMillis()) {
                emit(AlertLevel.P2, "慢请求：" + method + " " + path,
                        "slow|" + method + "|" + path, method, path, status, millis, traceId);
            }
        } catch (Throwable t) {
            // 记录链路异常绝不外溢：响应已经写出，这里只留本地痕迹
            log.warn("[访问日志] 事件构造失败（不影响响应）: {}", t.toString());
        }
    }

    private void emit(AlertLevel level, String title, String aggregateKey, String method,
                      String path, int status, long millis, String traceId) {
        AlertEvent.Builder builder = events.builder(EventTypes.ACCESS_LOG)
                .level(level)
                .title(title)
                .message("方法：" + method + "\n"
                        + "路径：" + path + "\n"
                        + "状态码：" + status + "\n"
                        + "耗时：" + millis + "ms")
                .source(method + " " + path)
                .aggregateKey(aggregateKey);
        if (traceId != null) {
            builder.traceId(traceId);
        }
        sink.emit(builder.build());
    }

    private static String traceId() {
        String value = MDC.get(MdcKeys.TRACE_ID);
        return value != null && TRACE_ID.matcher(value).matches() ? value : null;
    }
}
