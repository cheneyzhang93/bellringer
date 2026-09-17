package io.github.cheneyzhang93.bellringer.starter.async;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * MDC 上下文向线程池任务传播（异步子线程继承父线程 trace_id/span_id/user_id）。
 *
 * <p>以 {@code TaskDecorator} Bean 形式提供：Spring Boot 自动配置的 applicationTaskExecutor
 * 会自动应用；宿主自定义线程池可手动 {@code setTaskDecorator} 复用。任务终了恢复原上下文，
 * 防止线程池复用串扰。
 */
public class BellringerTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        return new Runnable() {
            @Override
            public void run() {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                restore(parentContext);
                try {
                    runnable.run();
                } finally {
                    restore(previous);
                }
            }
        };
    }

    private static void restore(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
