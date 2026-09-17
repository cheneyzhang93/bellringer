package io.github.cheneyzhang93.bellringer.starter.report;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 上报通道状态（F1：401 后停止上报并本地告警一次）。
 *
 * <p>{@link #stop(String)} 仅在首次停止转换时返回 true——上报器与心跳共享同一状态，
 * 保证 Token 失效后的本地告警全局恰好一次。
 */
public class ReportState {

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private volatile String stopReason;

    public boolean isStopped() {
        return stopped.get();
    }

    public String getStopReason() {
        return stopReason;
    }

    public boolean stop(String reason) {
        if (stopped.compareAndSet(false, true)) {
            stopReason = reason;
            return true;
        }
        return false;
    }
}
