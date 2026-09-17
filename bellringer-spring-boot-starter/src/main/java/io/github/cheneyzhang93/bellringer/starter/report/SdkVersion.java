package io.github.cheneyzhang93.bellringer.starter.report;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * SDK 版本（信封 sdkVersion 字段来源；构建期由 resource filtering 注入，见 version.properties）。
 */
public final class SdkVersion {

    private static final String UNKNOWN = "unknown";

    private static final String VERSION = load();

    private SdkVersion() {
    }

    public static String current() {
        return VERSION;
    }

    private static String load() {
        InputStream in = SdkVersion.class
                .getResourceAsStream("/io/github/cheneyzhang93/bellringer/starter/version.properties");
        if (in == null) {
            return UNKNOWN;
        }
        try {
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            return version == null || version.trim().isEmpty() ? UNKNOWN : version.trim();
        } catch (IOException e) {
            return UNKNOWN;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // 读取已完成，关闭失败无影响
            }
        }
    }
}
