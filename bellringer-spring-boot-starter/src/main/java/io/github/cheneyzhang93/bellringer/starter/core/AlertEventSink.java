package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;

/**
 * 事件总线入口（S3 事件源 → 双出口管道）。
 *
 * <p>实现方必须非阻塞：本方法由业务线程直接调用（ERROR 出口/拦截器/定时任务等），
 * 任何阻塞与异常都不得外溢回业务路径。
 */
public interface AlertEventSink {

    void emit(AlertEvent event);
}
