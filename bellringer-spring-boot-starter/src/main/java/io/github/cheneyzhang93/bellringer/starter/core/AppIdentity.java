package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.InstanceIds;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * 统一应用标识（F5 冻结）：贯通 trace service.name / metrics job / 告警 app / 心跳 app。
 *
 * <p>取值链（刻意 fail-fast，宁可启动失败也不产生互踩的匿名事件源）：
 * {@code observability.app} → {@code spring.application.name} → 抛异常终止启动。
 * 实例标识＝{@code hostname:pid}（{@link InstanceIds}）；环境＝
 * {@code observability.env} → 首个激活 profile → {@code default}。
 */
public final class AppIdentity {

    private final String app;

    private final String instance;

    private final String env;

    AppIdentity(String app, String instance, String env) {
        this.app = app;
        this.instance = instance;
        this.env = env;
    }

    /** 解析应用标识；app 缺失时抛 {@link IllegalStateException} 终止启动。 */
    public static AppIdentity resolve(BellringerProperties properties, Environment environment) {
        String app = firstNonBlank(properties.getApp(), environment.getProperty("spring.application.name"));
        if (app == null) {
            throw new IllegalStateException("敲钟人：缺少应用标识 —— 请配置 observability.app 或 spring.application.name"
                    + "（F5 冻结取值链，刻意 fail-fast，避免匿名事件源在控制台互踩）");
        }
        String env = firstNonBlank(properties.getEnv(), activeProfile(environment), "default");
        return new AppIdentity(app, defaultInstance(), env);
    }

    public String getApp() {
        return app;
    }

    public String getInstance() {
        return instance;
    }

    public String getEnv() {
        return env;
    }

    @Override
    public String toString() {
        return app + "@" + instance + "(" + env + ")";
    }

    /** hostname:pid；优先 JVM 运行时名（pid@host），异常时退化为 host:0。 */
    private static String defaultInstance() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int at = runtimeName.indexOf('@');
            if (at > 0 && at < runtimeName.length() - 1) {
                String pidPart = runtimeName.substring(0, at);
                String hostPart = runtimeName.substring(at + 1);
                if (isDigits(pidPart) && hostPart.indexOf(':') < 0) {
                    return InstanceIds.of(hostPart, Long.parseLong(pidPart));
                }
            }
        } catch (RuntimeException ignored) {
            // 退化路径：取不到 pid 时用 host:0，配合 startedAt 仍可区分重启
        }
        return InstanceIds.of(fallbackHostname(), 0L);
    }

    private static String fallbackHostname() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.trim().isEmpty() && hostName.indexOf(':') < 0) {
                return hostName;
            }
        } catch (Exception ignored) {
            // 继续退化
        }
        return "unknown-host";
    }

    private static String activeProfile(Environment environment) {
        if (environment instanceof ConfigurableEnvironment) {
            String[] profiles = ((ConfigurableEnvironment) environment).getActiveProfiles();
            if (profiles.length > 0) {
                return profiles[0];
            }
        }
        return environment.getProperty("spring.profiles.active");
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
