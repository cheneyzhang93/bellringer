package io.github.cheneyzhang93.bellringer.starter.core;

/**
 * 应用实例启动时刻（epoch millis，装配期定格）。
 *
 * <p>F2 语义：心跳 {@code startedAt} 是启动时刻而非发送时刻，配合 instance 区分重启；
 * 独立成 Bean 便于测试断言与多组件共享同一取值。
 */
public final class ApplicationStartedAt {

    private final long epochMillis;

    public ApplicationStartedAt(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    public long getEpochMillis() {
        return epochMillis;
    }
}
