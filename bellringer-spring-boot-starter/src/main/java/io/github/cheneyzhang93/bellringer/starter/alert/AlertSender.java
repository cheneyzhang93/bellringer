package io.github.cheneyzhang93.bellringer.starter.alert;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;

/**
 * 推送出口 SPI（F7 冻结签名）。
 *
 * <p>实现方可自定义通道（短信网关、自建服务等）；装配为 Spring Bean 即被复合收集，
 * 推送出口会把同一条告警按注册顺序发送给全部 {@code AlertSender}。
 *
 * <p>约定：推送失败＝单次尝试＋日志，不重试（重试语义只属于 HttpEventReporter 上报出口）；
 * 实现必须自行捕获网络异常并向调用方抛出，由出口统一记录现场。
 */
public interface AlertSender {

    /** 通道名（日志与诊断用，如 dingtalk / wecom / feishu / logging）。 */
    String name();

    /**
     * 发送一条告警。
     *
     * @param event    告警事件（含 level / type / traceId 等完整上下文）
     * @param markdown 统一渲染的 markdown 正文
     * @throws Exception 发送失败（由出口记录，不向上游业务线程传播）
     */
    void send(AlertEvent event, String markdown) throws Exception;
}
