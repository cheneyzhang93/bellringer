package io.github.cheneyzhang93.bellringer.protocol;

/**
 * 告警级别（F4 冻结：P0 / P1 / P2，缺省 P2；新增级别属小版本变更）。
 *
 * <p>P0＝致命：系统不可用或大面积失败，控制台走升级链路必达；
 * P1＝严重：单点故障或关键链路错误，通知处理组；
 * P2＝一般：低风险事件，以记录与聚合为主。
 */
public enum AlertLevel {
    P0,
    P1,
    P2
}
