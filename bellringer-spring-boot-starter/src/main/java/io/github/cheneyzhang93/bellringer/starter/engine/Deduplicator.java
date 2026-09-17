package io.github.cheneyzhang93.bellringer.starter.engine;

/**
 * 推送出口去重（F6：窗口内同 {app,type,aggregateKey} 只推送首条；上报出口不经过本接口）。
 *
 * <p>契约：返回 true＝窗口内首条（放行）；false＝窗口内重复（丢弃）。
 * 实现必须 fail-open——任何内部故障（Redis 不可达等）一律返回 true，宁可重复不可漏发。
 */
public interface Deduplicator {

    boolean firstWithinWindow(String app, String type, String aggregateKey);
}
