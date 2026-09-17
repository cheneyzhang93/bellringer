package io.github.cheneyzhang93.bellringer.starter.source;

/** 栈帧分类：区分应用代码与基础设施代码（java./框架/本 SDK）。 */
public final class AppFrames {

    /** 本 SDK 包前缀：自身上报链路产生的日志与栈帧必须排除，防止回环与自指。 */
    public static final String OWN_PACKAGE_PREFIX = "io.github.cheneyzhang93.bellringer";

    private static final String[] INFRA_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.springframework.", "org.apache.", "ch.qos.", "org.slf4j.",
            "com.fasterxml.", "org.mybatis.", "org.jboss.", "net.sf.",
            OWN_PACKAGE_PREFIX + "."
    };

    private AppFrames() {
    }

    /** 是否应用代码（非 JDK / 非主流框架 / 非本 SDK）。 */
    public static boolean isAppFrame(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        for (String prefix : INFRA_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /** 是否本 SDK 自身 logger（统一出口错误源必须跳过，否则"上报日志 → 事件 → 上报日志"回环）。 */
    public static boolean isOwnLogger(String loggerName) {
        return loggerName != null && loggerName.startsWith(OWN_PACKAGE_PREFIX);
    }
}
