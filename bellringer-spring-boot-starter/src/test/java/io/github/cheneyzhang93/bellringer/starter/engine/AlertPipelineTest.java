package io.github.cheneyzhang93.bellringer.starter.engine;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.alert.AlertSender;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import io.github.cheneyzhang93.bellringer.starter.core.ReportOutlet;
import io.github.cheneyzhang93.bellringer.starter.testing.TestEvents;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管道验收（F6/F7）：双出口分离、去重只作用于推送出口、SPI 链失败隔离、非法事件在总线口丢弃。
 */
class AlertPipelineTest {

    private static final long AWAIT_MILLIS = 5000L;

    private BellringerProperties properties;

    private AppIdentity identity;

    private ThreadPoolTaskExecutor executor;

    private List<AlertEvent> reported;

    private DefaultListableBeanFactory beanFactory;

    private RecordingSender first;

    private RecordingSender second;

    private AlertPipeline pipeline;

    @BeforeEach
    void setUp() {
        properties = new BellringerProperties();
        properties.setApp("payments-api");
        properties.setEnv("test");
        identity = AppIdentity.resolve(properties, new MockEnvironment());

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("test-alert-");
        executor.initialize();

        reported = new CopyOnWriteArrayList<AlertEvent>();
        ReportOutlet outlet = new ReportOutlet() {
            @Override
            public void enqueue(AlertEvent event) {
                reported.add(event);
            }
        };
        beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("reportOutlet", outlet);

        first = new RecordingSender("first");
        second = new RecordingSender("second");
        pipeline = new AlertPipeline(properties, Arrays.<AlertSender>asList(first, second),
                new InMemoryDeduplicator(300), new MarkdownRenderer(properties, identity), executor,
                beanFactory.getBeanProvider(ReportOutlet.class));
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void emitReachesBothOutlets() throws Exception {
        AlertEvent event = TestEvents.valid("payments-api");

        pipeline.emit(event);

        assertThat(first.awaitCount(1)).isTrue();
        assertThat(first.received).hasSize(1);
        assertThat(first.received.get(0).getEventId()).isEqualTo(event.getEventId());
        assertThat(first.markdowns.get(0)).contains("单元测试事件");
        assertThat(reported).containsExactly(event);
    }

    @Test
    void allSendersReceiveEachEvent() throws Exception {
        pipeline.emit(TestEvents.valid("payments-api"));

        assertThat(first.awaitCount(1)).isTrue();
        assertThat(second.awaitCount(1)).isTrue();
        assertThat(first.received).hasSize(1);
        assertThat(second.received).hasSize(1);
    }

    @Test
    void reportOutletStaysFullEvenWhenPushIsDeduplicated() throws Exception {
        AlertEvent firstEvent = TestEvents.valid("payments-api", "system-error", "Order#pay");
        AlertEvent duplicate = TestEvents.valid("payments-api", "system-error", "Order#pay");

        pipeline.emit(firstEvent);
        assertThat(first.awaitCount(1)).isTrue();
        pipeline.emit(duplicate);
        Thread.sleep(200);

        assertThat(first.received).hasSize(1);
        assertThat(reported).containsExactly(firstEvent, duplicate);
    }

    @Test
    void absentAggregateKeyIsPushedEveryTime() throws Exception {
        pipeline.emit(TestEvents.valid("payments-api"));
        assertThat(first.awaitCount(1)).isTrue();

        pipeline.emit(TestEvents.valid("payments-api"));

        assertThat(first.awaitCount(2)).isTrue();
        assertThat(first.received).hasSize(2);
    }

    @Test
    void disabledDedupPushesEveryTime() throws Exception {
        properties.getDedup().setEnabled(false);
        AlertEvent event = TestEvents.valid("payments-api", "system-error", "same-key");
        pipeline.emit(event);
        assertThat(first.awaitCount(1)).isTrue();

        pipeline.emit(event);

        assertThat(first.awaitCount(2)).isTrue();
        assertThat(first.received).hasSize(2);
    }

    @Test
    void invalidEventIsDroppedAtBusEntry() throws Exception {
        AlertEvent invalid = TestEvents.sample("payments-api").type("Not-Kebab").build();

        pipeline.emit(invalid);
        Thread.sleep(200);

        assertThat(first.received).isEmpty();
        assertThat(reported).isEmpty();
    }

    @Test
    void senderFailureDoesNotBlockOtherSenders() throws Exception {
        first.failWith = new IllegalStateException("webhook 挂了");

        pipeline.emit(TestEvents.valid("payments-api"));

        assertThat(second.awaitCount(1)).isTrue();
        assertThat(second.received).hasSize(1);
        assertThat(reported).hasSize(1);
    }

    @Test
    void pushLocalSkipsReportOutlet() throws Exception {
        pipeline.pushLocal(TestEvents.valid("payments-api"));

        assertThat(first.awaitCount(1)).isTrue();
        assertThat(first.received).hasSize(1);
        assertThat(reported).isEmpty();
    }

    @Test
    void missingReportOutletStillPushes() throws Exception {
        DefaultListableBeanFactory emptyFactory = new DefaultListableBeanFactory();
        ObjectProvider<ReportOutlet> none = emptyFactory.getBeanProvider(ReportOutlet.class);
        AlertPipeline localOnly = new AlertPipeline(properties, Arrays.<AlertSender>asList(first, second),
                new InMemoryDeduplicator(300), new MarkdownRenderer(properties, identity), executor, none);

        localOnly.emit(TestEvents.valid("payments-api"));

        assertThat(first.awaitCount(1)).isTrue();
        assertThat(reported).isEmpty();
    }

    static final class RecordingSender implements AlertSender {

        private final String name;

        final List<AlertEvent> received = new CopyOnWriteArrayList<AlertEvent>();

        final List<String> markdowns = new CopyOnWriteArrayList<String>();

        volatile RuntimeException failWith;

        RecordingSender(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void send(AlertEvent event, String markdown) {
            received.add(event);
            markdowns.add(markdown);
            if (failWith != null) {
                throw failWith;
            }
        }

        boolean awaitCount(int expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                if (received.size() >= expected) {
                    return true;
                }
                Thread.sleep(20);
            }
            return received.size() >= expected;
        }
    }
}
