package io.github.cheneyzhang93.bellringer.starter.source.errorlog;

import ch.qos.logback.classic.LoggerContext;
import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import com.example.demo.DemoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ERROR 统一出口验收：ERROR 级捕获、回环防护（本 SDK logger / 低于 ERROR）、根因聚合口径、卸载即静默。
 */
class ErrorLogEventSourceTest {

    private static final String APP_LOGGER = "com.example.demo.OrderService";

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    private final RecordingSink sink = new RecordingSink();

    private ErrorLogEventSource source;

    @AfterEach
    void unmount() {
        if (source != null) {
            source.stop();
        }
    }

    @Test
    void errorLogBecomesP1SystemErrorEvent() {
        mount();

        LoggerFactory.getLogger(APP_LOGGER).error("下单失败 bizId=42");

        AlertEvent event = sink.awaitFirst();
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(EventTypes.SYSTEM_ERROR);
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(event.getApp()).isEqualTo("demo");
        assertThat(event.getAggregateKey()).isEqualTo("no-exception|" + APP_LOGGER);
        assertThat(event.getTitle()).contains("no-exception").contains(APP_LOGGER);
        assertThat(event.getMessage()).contains("日志器：" + APP_LOGGER).contains("下单失败 bizId=42");
    }

    @Test
    void throwableRootCauseDrivesAggregationKey() {
        mount();
        Throwable failure = catchThrowable(() -> DemoService.failWith("演示异常"));

        LoggerFactory.getLogger(APP_LOGGER).error("订单处理失败", failure);

        AlertEvent event = sink.awaitFirst();
        assertThat(event).isNotNull();
        assertThat(event.getAggregateKey())
                .startsWith("java.lang.IllegalStateException|com.example.demo.DemoService#failWith");
        assertThat(event.getMessage()).contains("抛出点：com.example.demo.DemoService#failWith");
        assertThat(event.getMessage()).contains("应用栈：");
    }

    @Test
    void ownLoggerErrorsAreIgnored() {
        mount();

        LoggerFactory.getLogger("io.github.cheneyzhang93.bellringer.starter.source.errorlog.SelfCheck")
                .error("上报链路自身的错误不得回环成事件");

        sleep(100L);
        assertThat(sink.count()).isZero();
    }

    @Test
    void belowErrorLevelIsIgnored() {
        mount();

        LoggerFactory.getLogger(APP_LOGGER).warn("只是警告");

        sleep(100L);
        assertThat(sink.count()).isZero();
    }

    @Test
    void detachSilencesTheOutlet() {
        mount();
        source.stop();

        LoggerFactory.getLogger(APP_LOGGER).error("卸载后的错误不再上报");

        sleep(100L);
        assertThat(sink.count()).isZero();
    }

    @Test
    void hugeMessageIsTruncated() {
        mount();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            text.append('x');
        }

        LoggerFactory.getLogger(APP_LOGGER).error(text.toString());

        assertThat(sink.awaitFirst().getMessage()).contains("已截断");
    }

    private void mount() {
        ErrorLogAppender appender = new ErrorLogAppender(new AlertEventFactory(TestIdentities.app("demo")), sink);
        source = new ErrorLogEventSource(context, appender);
        source.start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
