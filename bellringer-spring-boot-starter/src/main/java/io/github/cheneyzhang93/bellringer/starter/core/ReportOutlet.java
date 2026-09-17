package io.github.cheneyzhang93.bellringer.starter.core;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;

/**
 * 上报出口（F1/F6：全量事件 → 控制台，不受去重影响）。
 *
 * <p>实现方必须非阻塞：入队到有界队列即可，队列满时丢弃计数，绝不阻塞业务线程。
 */
public interface ReportOutlet {

    void enqueue(AlertEvent event);
}
