package io.github.cheneyzhang93.bellringer.starter.testing;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 事件采集桩：记录全部 emit，供事件源测试断言（含异步等待工具）。 */
public final class RecordingSink implements AlertEventSink {

    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    private static final long POLL_INTERVAL_MILLIS = 20L;

    private final List<AlertEvent> events = Collections.synchronizedList(new ArrayList<AlertEvent>());

    @Override
    public void emit(AlertEvent event) {
        events.add(event);
    }

    public List<AlertEvent> events() {
        synchronized (events) {
            return new ArrayList<AlertEvent>(events);
        }
    }

    public int count() {
        return events.size();
    }

    public AlertEvent first() {
        synchronized (events) {
            return events.isEmpty() ? null : events.get(0);
        }
    }

    /** 轮询等待事件数达到期望值（异步链路/定时探测用）。 */
    public boolean awaitCount(int expected) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (count() >= expected) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return count() >= expected;
            }
        }
        return count() >= expected;
    }

    public AlertEvent awaitFirst() {
        return awaitCount(1) ? first() : null;
    }
}
