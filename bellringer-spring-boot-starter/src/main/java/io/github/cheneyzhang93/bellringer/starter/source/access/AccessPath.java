package io.github.cheneyzhang93.bellringer.starter.source.access;

import java.util.regex.Pattern;

/**
 * 访问路径归一化：把路径中的标识型片段替换为 {@code {id}}，避免每个主键生成一条独立聚合键。
 *
 * <p>保留静态段（如 {@code /orders/{id}/items}），数字段与 UUID 段（带/不带连字符）归一。
 * {@code ;jsessionid=...} 之类路径参数在归一前剥离。
 */
public final class AccessPath {

    private static final Pattern NUMERIC = Pattern.compile("^\\d+$");

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern HEX32 = Pattern.compile("^[0-9a-fA-F]{32}$");

    private AccessPath() {
    }

    public static String normalize(String requestUri) {
        if (requestUri == null || requestUri.isEmpty()) {
            return "/";
        }
        String uri = requestUri;
        int params = uri.indexOf(';');
        if (params >= 0) {
            uri = uri.substring(0, params);
        }
        if (uri.isEmpty()) {
            return "/";
        }
        String[] segments = uri.split("/", -1);
        StringBuilder sb = new StringBuilder(uri.length());
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(isIdentifier(segments[i]) ? "{id}" : segments[i]);
        }
        return sb.toString();
    }

    private static boolean isIdentifier(String segment) {
        return NUMERIC.matcher(segment).matches()
                || UUID.matcher(segment).matches()
                || HEX32.matcher(segment).matches();
    }
}
