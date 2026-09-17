package io.github.cheneyzhang93.bellringer.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** aggregateKey 约定冻结面：故障/恢复信号前缀与主体解析（两仓共用的唯一事实来源）。 */
class AggregateKeysContractTest {

    @Test
    void frozenPrefixes() {
        assertEquals("down|", AggregateKeys.DOWN_PREFIX);
        assertEquals("up|", AggregateKeys.UP_PREFIX);
    }

    @Test
    void buildsSignalsForSameSubject() {
        assertEquals("down|order-db", AggregateKeys.down("order-db"));
        assertEquals("up|order-db", AggregateKeys.up("order-db"));
        assertEquals("order-db", AggregateKeys.subject(AggregateKeys.down("order-db")));
        assertEquals("order-db", AggregateKeys.subject(AggregateKeys.up("order-db")));
    }

    @Test
    void classifiesSignals() {
        assertTrue(AggregateKeys.isFailure(AggregateKeys.down("order-db")));
        assertFalse(AggregateKeys.isRecovery(AggregateKeys.down("order-db")));
        assertTrue(AggregateKeys.isRecovery(AggregateKeys.up("order-db")));
        assertFalse(AggregateKeys.isFailure(AggregateKeys.up("order-db")));
        assertFalse(AggregateKeys.isFailure(null));
        assertFalse(AggregateKeys.isRecovery(null));
    }

    @Test
    void plainAggregateKeyIsItsOwnSubject() {
        String plain = "OrderService.submit:NullPointerException";

        assertEquals(plain, AggregateKeys.subject(plain));
        assertFalse(AggregateKeys.isFailure(plain));
        assertFalse(AggregateKeys.isRecovery(plain));
        assertNull(AggregateKeys.subject(null));
    }
}
