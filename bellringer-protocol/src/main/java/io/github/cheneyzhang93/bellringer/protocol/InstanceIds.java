package io.github.cheneyzhang93.bellringer.protocol;

import java.util.regex.Pattern;

/**
 * 实例标识约束（F2/F4 冻结）：{@code {hostname}:{pid}}，配合 startedAt 区分重启。
 *
 * <p>生产侧用 {@link #of(String, long)} 生成，消费侧用 {@link #isValid(String)} 校验。
 */
public final class InstanceIds {

    /** 规则：hostname 非空且不含冒号，pid 为 1~10 位十进制数字。 */
    public static final String PATTERN = "^[^:]+:[0-9]{1,10}$";

    private static final Pattern REGEX = Pattern.compile(PATTERN);

    /** 生成标准实例标识。 */
    public static String of(String hostname, long pid) {
        return hostname + ":" + pid;
    }

    /** 校验实例标识是否符合 hostname:pid 规则。 */
    public static boolean isValid(String value) {
        return value != null && REGEX.matcher(value).matches();
    }

    private InstanceIds() {
    }
}
