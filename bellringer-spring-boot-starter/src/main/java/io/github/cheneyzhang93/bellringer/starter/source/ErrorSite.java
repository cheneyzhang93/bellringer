package io.github.cheneyzhang93.bellringer.starter.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 错误定位信息（异常根因 + 抛出点）：ERROR 出口与定时任务失败源共用同一套聚合口径。
 *
 * <p>{@code aggregateKey = 根因类|抛出点}：同一根因同一位置的重复错误在窗口内只推送首条。
 */
public final class ErrorSite {

    /** 应用栈帧数量上限（详情里只展示这么多，避免把整棵树灌进告警）。 */
    private static final int MAX_APP_FRAMES = 5;

    private final String rootCauseClass;

    private final String throwSite;

    private final List<String> appFrames;

    private ErrorSite(String rootCauseClass, String throwSite, List<String> appFrames) {
        this.rootCauseClass = rootCauseClass;
        this.throwSite = throwSite;
        this.appFrames = appFrames;
    }

    /** 从异常链提取：最深 cause 为根因类；根因栈里第一个应用帧为抛出点。 */
    public static ErrorSite of(Throwable throwable) {
        if (throwable == null) {
            return new ErrorSite("unknown", "unknown", Collections.<String>emptyList());
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        List<String> frames = new ArrayList<String>(MAX_APP_FRAMES);
        String throwSite = null;
        for (StackTraceElement element : root.getStackTrace()) {
            if (AppFrames.isAppFrame(element.getClassName())) {
                if (throwSite == null) {
                    throwSite = formatFrame(element);
                }
                if (frames.size() < MAX_APP_FRAMES) {
                    frames.add(formatFrame(element));
                }
            }
        }
        if (throwSite == null) {
            throwSite = root.getStackTrace().length > 0 ? formatFrame(root.getStackTrace()[0]) : "unknown";
        }
        return new ErrorSite(root.getClass().getName(), throwSite, frames);
    }

    /** 由已提取好的根因类与栈帧构造（logback IThrowableProxy 路径复用同一聚合口径）。 */
    public static ErrorSite from(String rootCauseClass, List<String> rootFrames) {
        List<String> frames = new ArrayList<String>(MAX_APP_FRAMES);
        String throwSite = null;
        for (String frame : rootFrames) {
            String className = classNameOf(frame);
            if (AppFrames.isAppFrame(className)) {
                if (throwSite == null) {
                    throwSite = frame;
                }
                if (frames.size() < MAX_APP_FRAMES) {
                    frames.add(frame);
                }
            }
        }
        if (throwSite == null) {
            throwSite = rootFrames.isEmpty() ? "unknown" : rootFrames.get(0);
        }
        return new ErrorSite(rootCauseClass == null ? "unknown" : rootCauseClass, throwSite, frames);
    }

    /** 聚合去重键：根因类|抛出点。 */
    public String aggregateKey() {
        return rootCauseClass + "|" + throwSite;
    }

    public String getRootCauseClass() {
        return rootCauseClass;
    }

    public String getThrowSite() {
        return throwSite;
    }

    public List<String> getAppFrames() {
        return appFrames;
    }

    public String simpleRootCause() {
        int dot = rootCauseClass.lastIndexOf('.');
        return dot < 0 ? rootCauseClass : rootCauseClass.substring(dot + 1);
    }

    /** 统一栈帧文本：{@code className#method(file:line)}（logback 代理栈帧路径复用同一格式）。 */
    public static String formatFrame(StackTraceElement element) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(element.getClassName()).append('#').append(element.getMethodName());
        if (element.getFileName() != null) {
            sb.append('(').append(element.getFileName());
            if (element.getLineNumber() > 0) {
                sb.append(':').append(element.getLineNumber());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /** 从 "{className}#method(file:line)" 反解类名。 */
    private static String classNameOf(String frame) {
        int hash = frame.indexOf('#');
        return hash < 0 ? frame : frame.substring(0, hash);
    }
}
