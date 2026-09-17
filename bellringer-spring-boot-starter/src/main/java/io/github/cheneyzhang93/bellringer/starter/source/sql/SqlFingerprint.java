package io.github.cheneyzhang93.bellringer.starter.source.sql;

/**
 * SQL 指纹：归一化 SQL 文本用于聚合（同类慢 SQL 归为一条），不保留参数值。
 *
 * <p>规则：去注释 → 字符串/数字/IN 列表字面量 → {@code ?} → 折叠空白 → 转小写。
 */
public final class SqlFingerprint {

    private SqlFingerprint() {
    }

    public static String of(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "unknown-sql";
        }
        String normalized = stripComments(sql);
        normalized = replaceLiterals(normalized);
        normalized = normalized.replaceAll("\\s+", " ").trim().toLowerCase();
        return normalized;
    }

    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                int end = skipQuoted(sql, i);
                sb.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int newline = sql.indexOf('\n', i);
                i = newline < 0 ? sql.length() : newline + 1;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int close = sql.indexOf("*/", i + 2);
                i = close < 0 ? sql.length() : close + 2;
                sb.append(' ');
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String replaceLiterals(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(sql, i);
                sb.append('?');
                continue;
            }
            if (Character.isDigit(c) && !isWordChar(i > 0 ? sql.charAt(i - 1) : ' ')) {
                while (i < sql.length() && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                sb.append('?');
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 跳过引号包裹的字面量（含转义），返回结束引号之后的下标。 */
    private static int skipQuoted(String sql, int start) {
        char quote = sql.charAt(start);
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\' && i + 1 < sql.length()) {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2; // SQL 转义写法：'' 表示一个引号
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
