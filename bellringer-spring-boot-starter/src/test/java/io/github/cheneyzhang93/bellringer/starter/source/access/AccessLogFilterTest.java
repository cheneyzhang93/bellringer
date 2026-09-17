package io.github.cheneyzhang93.bellringer.starter.source.access;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import io.github.cheneyzhang93.bellringer.starter.trace.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问日志事件源验收：5xx/慢请求分级、路径归一聚合、traceId 透传（含非法值丢弃）、正常请求零事件。
 */
class AccessLogFilterTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    private final RecordingSink sink = new RecordingSink();

    private final BellringerProperties.Sources.AccessLog config = new BellringerProperties.Sources.AccessLog();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void serverErrorEmitsP1WithNormalizedPath() throws Exception {
        filter().doFilter(get("/orders/889900/items"), new MockHttpServletResponse(),
                (request, response) -> ((MockHttpServletResponse) response).setStatus(500));

        AlertEvent event = sink.first();
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(EventTypes.ACCESS_LOG);
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(event.getAggregateKey()).isEqualTo("server-error|GET|/orders/{id}/items");
        assertThat(event.getMessage()).contains("状态码：500");
        assertThat(event.getSource()).isEqualTo("GET /orders/{id}/items");
    }

    @Test
    void slowRequestEmitsP2() throws Exception {
        config.setSlowMillis(10L);
        filter().doFilter(get("/orders/889900"), new MockHttpServletResponse(), (request, response) -> sleep(40L));

        AlertEvent event = sink.first();
        assertThat(event).isNotNull();
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P2);
        assertThat(event.getAggregateKey()).isEqualTo("slow|GET|/orders/{id}");
        assertThat(event.getTitle()).contains("慢请求");
        assertThat(event.getMessage()).contains("耗时：");
    }

    @Test
    void fastSuccessfulRequestEmitsNothing() throws Exception {
        config.setSlowMillis(1000L);
        filter().doFilter(get("/orders/1"), new MockHttpServletResponse(), (request, response) -> {
        });

        assertThat(sink.count()).isZero();
    }

    @Test
    void serverErrorTakesPrecedenceOverSlowEvent() throws Exception {
        config.setSlowMillis(0L);
        filter().doFilter(get("/orders/1"), new MockHttpServletResponse(),
                (request, response) -> ((MockHttpServletResponse) response).setStatus(503));

        // 5xx 优先：只发一条 P1，不与慢请求事件叠加
        assertThat(sink.count()).isEqualTo(1);
        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
    }

    @Test
    void validTraceIdIsPropagated() throws Exception {
        MDC.put(MdcKeys.TRACE_ID, TRACE);
        filter().doFilter(get("/orders/1"), new MockHttpServletResponse(),
                (request, response) -> ((MockHttpServletResponse) response).setStatus(500));

        assertThat(sink.first().getTraceId()).isEqualTo(TRACE);
    }

    @Test
    void invalidTraceIdIsDroppedInsteadOfFailingContract() throws Exception {
        MDC.put(MdcKeys.TRACE_ID, "not-a-trace-id");
        filter().doFilter(get("/orders/1"), new MockHttpServletResponse(),
                (request, response) -> ((MockHttpServletResponse) response).setStatus(500));

        AlertEvent event = sink.first();
        assertThat(event.getTraceId()).isNull();
        assertThat(event.validate()).isEmpty();
    }

    @Test
    void filterSitsOutsideBusinessFilters() {
        assertThat(filter().getOrder()).isEqualTo(AccessLogFilter.ORDER);
    }

    private AccessLogFilter filter() {
        return new AccessLogFilter(config, new AlertEventFactory(TestIdentities.app("demo")), sink);
    }

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
