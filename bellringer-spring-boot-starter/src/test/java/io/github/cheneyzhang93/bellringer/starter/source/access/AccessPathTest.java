package io.github.cheneyzhang93.bellringer.starter.source.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 访问路径归一化验收：标识段替换、路径参数剥离、静态段保留。 */
class AccessPathTest {

    @Test
    void numericAndUuidSegmentsBecomePlaceholder() {
        assertThat(AccessPath.normalize("/orders/123")).isEqualTo("/orders/{id}");
        assertThat(AccessPath.normalize("/orders/123/items/456")).isEqualTo("/orders/{id}/items/{id}");
        assertThat(AccessPath.normalize("/orders/550e8400-e29b-41d4-a716-446655440000"))
                .isEqualTo("/orders/{id}");
        assertThat(AccessPath.normalize("/orders/8f14e45fceea167a5a36dedd4bea2543"))
                .isEqualTo("/orders/{id}");
    }

    @Test
    void staticSegmentsAreKept() {
        assertThat(AccessPath.normalize("/static/app.js")).isEqualTo("/static/app.js");
        assertThat(AccessPath.normalize("/api/v1/orders")).isEqualTo("/api/v1/orders");
    }

    @Test
    void pathParametersAreStripped() {
        assertThat(AccessPath.normalize("/orders;jsessionid=ABC123")).isEqualTo("/orders");
    }

    @Test
    void blankUriFallsBackToRoot() {
        assertThat(AccessPath.normalize(null)).isEqualTo("/");
        assertThat(AccessPath.normalize("")).isEqualTo("/");
        assertThat(AccessPath.normalize("/")).isEqualTo("/");
    }
}
