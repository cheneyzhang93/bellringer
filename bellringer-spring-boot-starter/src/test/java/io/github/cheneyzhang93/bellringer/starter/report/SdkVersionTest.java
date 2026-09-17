package io.github.cheneyzhang93.bellringer.starter.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK 版本验收：resource filtering 生效（信封 sdkVersion 不得退化为 unknown）。
 */
class SdkVersionTest {

    @Test
    void versionIsInjectedAtBuildTime() {
        assertThat(SdkVersion.current()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(SdkVersion.current()).isNotEqualTo("unknown");
    }
}
