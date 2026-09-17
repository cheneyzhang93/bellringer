package io.github.cheneyzhang93.bellringer.starter.async;

import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步线程命名（F5 贯通）：未自定义前缀的执行池统一采用 {@code {app}-async-}。
 *
 * <p>刻意不实现 {@code AsyncConfigurer}（F10.3：不与宿主 AsyncConfigurer 冲突），
 * 仅通过 {@link TaskExecutorCustomizer} 增量定制：宿主显式设置过前缀（非 Boot 缺省 "task-"）则尊重不动。
 */
public class BellringerTaskExecutorCustomizer implements TaskExecutorCustomizer {

    private static final String BOOT_DEFAULT_PREFIX = "task-";

    private final AppIdentity identity;

    public BellringerTaskExecutorCustomizer(AppIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void customize(ThreadPoolTaskExecutor taskExecutor) {
        String prefix = taskExecutor.getThreadNamePrefix();
        if (isUntouchedDefault(prefix)) {
            taskExecutor.setThreadNamePrefix(identity.getApp() + "-async-");
        }
    }

    /** 未经宿主显式设置的标志：null/空白，或仍是 Boot 属性缺省值 task-（仅精确相等才算）。 */
    private static boolean isUntouchedDefault(String prefix) {
        return prefix == null || prefix.trim().isEmpty() || BOOT_DEFAULT_PREFIX.equals(prefix);
    }
}
