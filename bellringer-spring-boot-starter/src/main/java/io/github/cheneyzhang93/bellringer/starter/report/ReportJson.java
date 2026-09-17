package io.github.cheneyzhang93.bellringer.starter.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 上报专用序列化器（独立于宿主 ObjectMapper，避免宿主自定义策略破坏契约形状）。
 *
 * <p>对齐协议契约：null 字段不输出（NON_NULL）、未知字段容忍（消费端向前兼容语义一致）。
 */
final class ReportJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ReportJson() {
    }
}
