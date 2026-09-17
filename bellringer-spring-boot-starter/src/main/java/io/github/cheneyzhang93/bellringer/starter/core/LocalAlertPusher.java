package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;

/**
 * 仅推送出口的本地投递（去重 + 渲染 + AlertSender 链），绝不进入上报出口。
 *
 * <p>用途：上报通道自身故障（如 401 停止上报）时发出本地告警——该告警不能经上报出口
 * （否则递归回环），只能走推送出口。
 */
public interface LocalAlertPusher {

    void pushLocal(AlertEvent event);
}
