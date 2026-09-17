package io.github.cheneyzhang93.bellringer.starter.source.errorlog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import io.github.cheneyzhang93.bellringer.starter.source.AppFrames;
import io.github.cheneyzhang93.bellringer.starter.source.ErrorSite;
import io.github.cheneyzhang93.bellringer.starter.trace.MdcKeys;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ERROR 统一出口（日志框架 ERROR 级事件 → {@code system-error} 事件）。
 *
 * <p>挂到 root logger，只收 ERROR 及以上；两类回环防护：
 * <ul>
 *   <li>本 SDK 自身 logger（{@link AppFrames#isOwnLogger}）一律跳过——上报链路自己打日志不会变成事件；</li>
 *   <li>logback {@code AppenderBase} 自带同线程重入保护，事件构造中的日志不会递归。</li>
 * </ul>
 *
 * <p>聚合键＝根因类|抛出点（与定时任务失败源同口径）；无异常的 ERROR 日志按 logger 名聚合。
 * append 内任何异常都被吞掉并降级为 logback 状态告警，绝不影响业务日志与业务线程。
 */
public class ErrorLogAppender extends AppenderBase<ILoggingEvent> {

    /** 无异常日志的聚合根因占位（{@link ErrorSite} 的根因类字段）。 */
    static final String NO_EXCEPTION = "no-exception";

    /** 日志正文截断长度：告警要可读，不搬运整个日志。 */
    private static final int MAX_MESSAGE_CHARS = 500;

    private static final Pattern TRACE_ID = Pattern.compile(AlertEvent.TRACE_ID_PATTERN);

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    public ErrorLogAppender(AlertEventFactory events, AlertEventSink sink) {
        this.events = events;
        this.sink = sink;
        setName("bellringer-error-log");
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null || !eventObject.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return;
        }
        String loggerName = eventObject.getLoggerName();
        if (AppFrames.isOwnLogger(loggerName)) {
            return;
        }
        try {
            emit(eventObject, loggerName);
        } catch (Exception e) {
            addError("敲钟人 ERROR 出口构造事件失败（已忽略）", e);
        }
    }

    private void emit(ILoggingEvent eventObject, String loggerName) {
        ErrorSite site = siteOf(eventObject, loggerName);
        String title = "ERROR：" + site.simpleRootCause() + " @ " + shortSite(site.getThrowSite());
        AlertEvent.Builder builder = events.builder(EventTypes.SYSTEM_ERROR)
                .level(AlertLevel.P1)
                .title(title)
                .message(message(eventObject, loggerName, site))
                .source(site.getThrowSite())
                .aggregateKey(site.aggregateKey());
        String traceId = traceId(eventObject.getMDCPropertyMap());
        if (traceId != null) {
            builder.traceId(traceId);
        }
        sink.emit(builder.build());
    }

    /** 有异常＝根因类|抛出点；无异常＝按 logger 名聚合（同 logger 的重复 ERROR 窗口内只推首条）。 */
    private static ErrorSite siteOf(ILoggingEvent eventObject, String loggerName) {
        IThrowableProxy proxy = eventObject.getThrowableProxy();
        if (proxy == null) {
            return ErrorSite.from(NO_EXCEPTION, Collections.singletonList(loggerName));
        }
        IThrowableProxy root = proxy;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        List<String> frames = new ArrayList<String>();
        StackTraceElementProxy[] stack = root.getStackTraceElementProxyArray();
        if (stack != null) {
            for (StackTraceElementProxy frame : stack) {
                if (frame != null && frame.getStackTraceElement() != null) {
                    frames.add(ErrorSite.formatFrame(frame.getStackTraceElement()));
                }
            }
        }
        return ErrorSite.from(root.getClassName(), frames);
    }

    private static String message(ILoggingEvent eventObject, String loggerName, ErrorSite site) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("日志器：").append(loggerName).append('\n');
        sb.append("线程：").append(eventObject.getThreadName()).append('\n');
        sb.append("根因：").append(site.getRootCauseClass());
        if (eventObject.getThrowableProxy() != null && eventObject.getThrowableProxy().getMessage() != null) {
            sb.append(": ").append(eventObject.getThrowableProxy().getMessage());
        }
        sb.append('\n');
        sb.append("抛出点：").append(site.getThrowSite()).append('\n');
        if (!site.getAppFrames().isEmpty()) {
            sb.append("应用栈：").append('\n');
            for (String frame : site.getAppFrames()) {
                sb.append("  ").append(frame).append('\n');
            }
        }
        sb.append("日志正文：").append(truncate(eventObject.getFormattedMessage()));
        return sb.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= MAX_MESSAGE_CHARS ? flat : flat.substring(0, MAX_MESSAGE_CHARS) + "…（已截断）";
    }

    /** 只透传合 F4 契约的 traceId（32 位小写 hex），其余留空避免整条事件校验失败。 */
    private static String traceId(Map<String, String> mdc) {
        if (mdc == null) {
            return null;
        }
        String value = mdc.get(MdcKeys.TRACE_ID);
        return value != null && TRACE_ID.matcher(value).matches() ? value : null;
    }

    private static String shortSite(String throwSite) {
        int paren = throwSite.lastIndexOf('(');
        return paren < 0 ? throwSite : throwSite.substring(0, paren);
    }
}
