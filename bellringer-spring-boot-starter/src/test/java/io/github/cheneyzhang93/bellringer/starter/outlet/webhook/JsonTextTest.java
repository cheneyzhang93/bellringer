package io.github.cheneyzhang93.bellringer.starter.outlet.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON 片段构造器验收：转义正确性与关键字面量映射（三家 webhook 报文的共同底座）。
 */
class JsonTextTest {

    @Test
    void escapesQuotesBackslashesAndControlChars() {
        assertThat(JsonText.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(JsonText.quote("line1\nline2\r\ttab")).isEqualTo("\"line1\\nline2\\r\\ttab\"");
        assertThat(JsonText.quote("bell\u0007")).isEqualTo("\"bell\\u0007\"");
    }

    @Test
    void keepsChineseCharactersRaw() {
        assertThat(JsonText.quote("告警")).isEqualTo("\"告警\"");
    }

    @Test
    void objectMapsValueKinds() {
        String json = JsonText.object("s", "text", "b", Boolean.TRUE, "n", Integer.valueOf(3),
                "nil", null, "nested", JsonText.raw(JsonText.object("k", "v")));

        assertThat(json).isEqualTo("{\"s\":\"text\",\"b\":true,\"n\":3,\"nil\":null,\"nested\":{\"k\":\"v\"}}");
    }

    @Test
    void arrayRendersMixedValues() {
        assertThat(JsonText.array("a", Integer.valueOf(1), Boolean.FALSE))
                .isEqualTo("[\"a\",1,false]");
        assertThat(JsonText.array()).isEqualTo("[]");
    }

    @Test
    void oddKeyValueCountIsRejected() {
        assertThatThrownBy(() -> JsonText.object("onlyKey"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rawIsInlinedVerbatim() {
        assertThat(JsonText.object("card", JsonText.raw("{\"a\":1}")))
                .isEqualTo("{\"card\":{\"a\":1}}");
    }
}
