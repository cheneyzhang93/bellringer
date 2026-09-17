package io.github.cheneyzhang93.bellringer.starter.source.schedule;

import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 把 {@link ScheduledTaskErrorHandler} 装进宿主 {@code TaskScheduler}（增量定制，不自建调度器）。
 *
 * <p>Boot 在 {@code TaskSchedulerBuilder.configure} 中按序应用全部 {@code TaskSchedulerCustomizer}，
 * 宿主若另有定制且排在其后，以宿主为准（不自作主张覆盖宿主显式配置）。
 */
public class ScheduledTaskErrorCustomizer implements TaskSchedulerCustomizer {

    private final ScheduledTaskErrorHandler errorHandler;

    public ScheduledTaskErrorCustomizer(ScheduledTaskErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public void customize(ThreadPoolTaskScheduler taskScheduler) {
        taskScheduler.setErrorHandler(errorHandler);
    }
}
