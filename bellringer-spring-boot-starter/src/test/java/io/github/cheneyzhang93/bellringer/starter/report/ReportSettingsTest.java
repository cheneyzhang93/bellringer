package io.github.cheneyzhang93.bellringer.starter.report;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上报配置验收（F1）：mode 含 http 时 endpoint/token 必填（显式配置错误启动即失败）。
 */
class ReportSettingsTest {

    @Test
    void webhookModeLeavesHttpChannelOff() {
        BellringerProperties properties = new BellringerProperties();

        ReportSettings settings = ReportSettings.from(properties);

        assertThat(settings.isHttpEnabled()).isFalse();
        assertThat(settings.getQueueSize()).isEqualTo(1024);
        assertThat(settings.getMaxRetries()).isEqualTo(5);
        assertThat(settings.getHeartbeatIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void httpModeRequiresEndpoint() {
        BellringerProperties properties = new BellringerProperties();
        properties.getReport().setMode(BellringerProperties.Report.Mode.HTTP);
        properties.getReport().setToken("tk");

        assertThatThrownBy(() -> ReportSettings.from(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void httpModeRequiresToken() {
        BellringerProperties properties = new BellringerProperties();
        properties.getReport().setMode(BellringerProperties.Report.Mode.BOTH);
        properties.getReport().setEndpoint("http://console.internal:8080");

        assertThatThrownBy(() -> ReportSettings.from(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token");
    }

    @Test
    void bothModeIsHttpEnabledAndTrimsTrailingSlashes() {
        BellringerProperties properties = new BellringerProperties();
        properties.getReport().setMode(BellringerProperties.Report.Mode.BOTH);
        properties.getReport().setEndpoint("  http://console.internal:8080///  ");
        properties.getReport().setToken("tk");

        ReportSettings settings = ReportSettings.from(properties);

        assertThat(settings.isHttpEnabled()).isTrue();
        assertThat(settings.getEndpoint()).isEqualTo("http://console.internal:8080");
        assertThat(settings.getToken()).isEqualTo("tk");
    }
}
