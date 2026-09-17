package io.github.cheneyzhang93.bellringer.starter.source.schedule;

import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.source.ErrorSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ErrorHandler;

/**
 * 定时任务失败事件源：包装 {@code TaskScheduler} 的错误处理，任务抛出的异常 → {@code scheduled-task-error}（P1）。
 *
 * <p>语义与 Spring 默认处理器一致（先记日志再吞掉异常，绝不打断后续调度），额外发一条事件；
 * 日志走本 SDK 自身 logger，不会回环到 ERROR 出口。
 * 聚合键＝根因类|抛出点，同一失败在去重窗口内只推首条。
 */
public class ScheduledTaskErrorHandler implements ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskErrorHandler.class);

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    public ScheduledTaskErrorHandler(AlertEventFactory events, AlertEventSink sink) {
        this.events = events;
        this.sink = sink;
    }

    @Override
    public void handleError(Throwable throwable) {
        log.error("[定时任务] 执行失败（调度不中断，继续后续触发）", throwable);
        try {
            ErrorSite site = ErrorSite.of(throwable);
            sink.emit(events.builder(EventTypes.SCHEDULED_TASK_ERROR)
                    .level(AlertLevel.P1)
                    .title("定时任务失败：" + site.simpleRootCause())
                    .message("定时任务执行抛出异常（任务名见调度线程日志）\n"
                            + "根因：" + site.getRootCauseClass() + "\n"
                            + "抛出点：" + site.getThrowSite() + "\n"
                            + "异常：" + throwable)
                    .source(site.getThrowSite())
                    .aggregateKey(site.aggregateKey())
                    .build());
        } catch (Exception e) {
            log.warn("[定时任务] 事件构造失败（不影响调度）: {}", e.toString());
        }
    }
}
