package io.github.cheneyzhang93.bellringer.starter.source.errorlog;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * ERROR 出口挂载器：容器刷新收尾时把 {@link ErrorLogAppender} 挂到 root logger，容器关闭时摘除。
 *
 * <p>用 {@link SmartLifecycle}（默认 phase＝最晚启动）而不是装配期直接挂载：保证生效前
 * 全部单例已就绪，且关闭顺序可控——摘除发生在业务日志上下文销毁之前。
 */
public class ErrorLogEventSource implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogEventSource.class);

    private final LoggerContext context;

    private final ErrorLogAppender appender;

    private volatile boolean running;

    public ErrorLogEventSource(LoggerContext context, ErrorLogAppender appender) {
        this.context = context;
        this.appender = appender;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        appender.setContext(context);
        appender.start();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
        running = true;
        log.info("[ERROR 出口] 已挂载 root logger：ERROR 及以上 → system-error（P1），本 SDK 自身日志已排除");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
