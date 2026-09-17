package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.testing.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * markdown 渲染验收：模板字段、可选项省略、trace 跳转行、超长截断。
 */
class MarkdownRendererTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    private BellringerProperties properties;

    private AppIdentity identity;

    private MarkdownRenderer renderer;

    @BeforeEach
    void setUp() {
        properties = new BellringerProperties();
        properties.setApp("payments-api");
        properties.setEnv("prod");
        identity = AppIdentity.resolve(properties, new MockEnvironment());
        renderer = new MarkdownRenderer(properties, identity);
    }

    @Test
    void rendersFrozenTemplateFields() {
        AlertEvent event = TestEvents.sample("payments-api").level(AlertLevel.P1).traceId(TRACE).build();

        String markdown = renderer.render(event);

        assertThat(markdown).contains("### [P1] 单元测试事件");
        assertThat(markdown).containsPattern("- \\*\\*时间\\*\\*：\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(markdown).contains("- **环境**：prod");
        assertThat(markdown).contains("- **实例**：" + identity.getInstance());
        assertThat(markdown).contains("- **级别**：P1(严重)");
        assertThat(markdown).contains("- **来源**：com.example.DemoService#run");
        assertThat(markdown).contains("- **trace_id**：`" + TRACE + "`");
        assertThat(markdown).contains("- **详情**：");
        assertThat(markdown).contains("异常摘要：boom");
    }

    @Test
    void omitsTraceSectionWhenTraceIdAbsent() {
        AlertEvent event = TestEvents.valid("payments-api");

        String markdown = renderer.render(event);

        assertThat(markdown).doesNotContain("trace_id");
        assertThat(markdown).doesNotContain("检索");
        assertThat(markdown).doesNotContain("排障指引");
    }

    @Test
    void rendersTraceUrlAndHintWhenConfigured() {
        properties.getAlert().setTraceUrlTemplate("https://grafana.internal/explore?traceId={traceId}");
        properties.getAlert().setTraceQueryHint("在日志平台检索 traceId={traceId}");
        AlertEvent event = TestEvents.sample("payments-api").traceId(TRACE).build();

        String markdown = renderer.render(event);

        assertThat(markdown).contains("- **检索**：https://grafana.internal/explore?traceId=" + TRACE);
        assertThat(markdown).contains("在日志平台检索 traceId=" + TRACE);
    }

    @Test
    void levelLabelsFollowFrozenMapping() {
        AlertEvent p0 = TestEvents.sample("payments-api").level(AlertLevel.P0).build();
        AlertEvent p1 = TestEvents.sample("payments-api").level(AlertLevel.P1).build();
        AlertEvent p2 = TestEvents.sample("payments-api").level(AlertLevel.P2).build();

        assertThat(renderer.render(p0)).contains("- **级别**：P0(致命)");
        assertThat(renderer.render(p1)).contains("- **级别**：P1(严重)");
        assertThat(renderer.render(p2)).contains("- **级别**：P2(一般)");
    }

    @Test
    void levelDefaultsToP2WhenUnset() {
        AlertEvent event = TestEvents.sample("payments-api").level(null).build();

        assertThat(renderer.render(event)).contains("### [P2] 单元测试事件");
    }

    @Test
    void longMessageIsTruncated() {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < MarkdownRenderer.MAX_MESSAGE_CHARS + 500; i++) {
            message.append('x');
        }
        AlertEvent event = TestEvents.sample("payments-api").message(message.toString()).build();

        String markdown = renderer.render(event);

        assertThat(markdown).contains("...(已截断)");
        assertThat(markdown.length()).isLessThan(message.length());
    }

    @Test
    void titleFallsBackToEventType() {
        AlertEvent event = TestEvents.sample("payments-api").title(null).type(EventTypes.SLOW_SQL).build();

        assertThat(renderer.render(event)).contains("### [P1] slow-sql");
    }
}
