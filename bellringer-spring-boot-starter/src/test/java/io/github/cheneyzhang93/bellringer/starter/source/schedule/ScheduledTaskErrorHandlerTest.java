package io.github.cheneyzhang93.bellringer.starter.source.schedule;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import com.example.demo.DemoService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 定时任务失败事件源验收：异常吞掉但事件送出、根因聚合、经自定义错误处理接入真实调度器。
 */
class ScheduledTaskErrorHandlerTest {

    private final RecordingSink sink = new RecordingSink();

    private final ScheduledTaskErrorHandler handler = new ScheduledTaskErrorHandler(
            new AlertEventFactory(TestIdentities.app("demo")), sink);

    @Test
    void emitsP1EventForFailedTask() {
        Throwable failure = catchThrowable(() -> DemoService.failWith("任务执行失败"));

        handler.handleError(failure);

        AlertEvent event = sink.first();
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(EventTypes.SCHEDULED_TASK_ERROR);
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(event.getTitle()).contains("定时任务失败").contains("IllegalStateException");
        assertThat(event.getAggregateKey())
                .startsWith("java.lang.IllegalStateException|com.example.demo.DemoService#failWith");
        assertThat(event.getMessage()).contains("抛出点：com.example.demo.DemoService#failWith");
    }

    @Test
    void failedConstructionNeverPropagatesToScheduler() {
        // handleError 是调度器异常出口：即便事件构造失败也必须静默返回
        handler.handleError(null);

        assertThat(sink.first()).isNotNull();
        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
    }

    @Test
    void customizerInstallsHandlerIntoRealScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("demo-sched-");
        new ScheduledTaskErrorCustomizer(handler).customize(scheduler);
        scheduler.initialize();
        try {
            scheduler.schedule(() -> DemoService.failWith("调度任务失败"), Instant.now());

            assertThat(sink.awaitCount(1)).as("调度器捕获的异常应转成事件").isTrue();
            assertThat(sink.first().getType()).isEqualTo(EventTypes.SCHEDULED_TASK_ERROR);
        } finally {
            scheduler.shutdown();
        }
    }
}
