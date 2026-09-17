package io.github.cheneyzhang93.bellringer.starter.engine;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内去重（缺省实现，零外部依赖；多实例部署时去重仅实例内生效——跨实例聚合属控制台职责）。
 *
 * <p>容量护栏 {@value #MAX_ENTRIES} 条：超限先清理过期项，仍超限则继续写入（缓存语义，宁可多占不可漏发）。
 */
public class InMemoryDeduplicator implements Deduplicator {

    static final int MAX_ENTRIES = 10_000;

    private final int windowSeconds;

    private final ConcurrentHashMap<String, Long> seenUntilMillis = new ConcurrentHashMap<String, Long>();

    public InMemoryDeduplicator(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean firstWithinWindow(String app, String type, String aggregateKey) {
        long now = System.currentTimeMillis();
        String key = app + "|" + type + "|" + aggregateKey;
        Long until = seenUntilMillis.get(key);
        if (until != null && until.longValue() > now) {
            return false;
        }
        if (seenUntilMillis.size() >= MAX_ENTRIES) {
            evictExpired(now);
        }
        seenUntilMillis.put(key, Long.valueOf(now + windowSeconds * 1000L));
        return true;
    }

    /** 测试可见：当前窗口内记录数。 */
    int size() {
        return seenUntilMillis.size();
    }

    private void evictExpired(long now) {
        for (Iterator<Map.Entry<String, Long>> it = seenUntilMillis.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().longValue() <= now) {
                it.remove();
            }
        }
    }
}
