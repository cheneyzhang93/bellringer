package io.github.cheneyzhang93.bellringer.starter.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内去重验收：窗口语义、键隔离、过期放行、容量护栏。
 */
class InMemoryDeduplicatorTest {

    @Test
    void firstCallPassesSecondIsSuppressedWithinWindow() {
        InMemoryDeduplicator deduplicator = new InMemoryDeduplicator(300);

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-1")).isTrue();
        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-1")).isFalse();
    }

    @Test
    void distinctKeysAreIndependent() {
        InMemoryDeduplicator deduplicator = new InMemoryDeduplicator(300);

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-1")).isTrue();
        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-2")).isTrue();
        assertThat(deduplicator.firstWithinWindow("app", "slow-sql", "key-1")).isTrue();
        assertThat(deduplicator.firstWithinWindow("other-app", "system-error", "key-1")).isTrue();
    }

    @Test
    void expiryAllowsNextWindow() throws InterruptedException {
        InMemoryDeduplicator deduplicator = new InMemoryDeduplicator(1);

        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-1")).isTrue();
        Thread.sleep(1100);
        assertThat(deduplicator.firstWithinWindow("app", "system-error", "key-1")).isTrue();
    }

    @Test
    void expiredEntriesAreEvictedUnderCapacityPressure() {
        InMemoryDeduplicator deduplicator = new InMemoryDeduplicator(0);

        for (int i = 0; i < InMemoryDeduplicator.MAX_ENTRIES + 50; i++) {
            deduplicator.firstWithinWindow("app", "system-error", "key-" + i);
        }

        assertThat(deduplicator.size()).isLessThanOrEqualTo(InMemoryDeduplicator.MAX_ENTRIES);
    }
}
