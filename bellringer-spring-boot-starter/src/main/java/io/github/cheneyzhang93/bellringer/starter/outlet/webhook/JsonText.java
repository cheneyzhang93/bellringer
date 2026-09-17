package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

/**
 * 极简 JSON 片段构造器（仅服务三家群机器人固定报文：字段少、形状固定，不引入序列化依赖）。
 *
 * <p>{@link #object(Object...)} 交替接收 key 与 value：key 必为 String；value 为 String 时自动转义加引号，
 * 为 Boolean/Number 时直出字面量，为 {@link Raw} 时原样内联（嵌套对象/数组）。
 */
public final class JsonText {

    private JsonText() {
    }

    /** 原样内联的 JSON 片段（嵌套对象/数组）。 */
    public static final class Raw {

        private final String json;

        private Raw(String json) {
            this.json = json;
        }

        @Override
        public String toString() {
            return json;
        }
    }

    public static Raw raw(String json) {
        return new Raw(json);
    }

    public static String object(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value 必须成对");
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(String.valueOf(keyValues[i]))).append(':').append(value(keyValues[i + 1]));
        }
        return sb.append('}').toString();
    }

    public static String array(Object... values) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(value(values[i]));
        }
        return sb.append(']').toString();
    }

    /** JSON 字符串转义（含控制字符；中文按 UTF-8 原样输出，webhook 侧可解析）。 */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Raw) {
            return value.toString();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }
}
