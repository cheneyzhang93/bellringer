package io.github.cheneyzhang93.bellringer.starter.async;

import io.github.cheneyzhang93.bellringer.starter.trace.MdcKeys;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MDC 跨线程传播验收：继承父快照、池线程零串扰、调用方上下文复原。 */
class BellringerTaskDecoratorTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    private final BellringerTaskDecorator decorator = new BellringerTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void taskInheritsParentSnapshot() {
        MDC.put(MdcKeys.TRACE_ID, TRACE);
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get(MdcKeys.TRACE_ID)));
        MDC.clear();
        decorated.run();
        assertThat(seen.get()).isEqualTo(TRACE);
    }

    @Test
    void stalePoolThreadContextDoesNotLeakIntoTask() {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get(MdcKeys.TRACE_ID)));
        MDC.put(MdcKeys.TRACE_ID, "stale");
        decorated.run();
        assertThat(seen.get()).isNull();
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isEqualTo("stale");
    }

    @Test
    void callerContextRestoredAfterTask() {
        MDC.put(MdcKeys.TRACE_ID, "parent");
        Runnable decorated = decorator.decorate(() -> { });
        MDC.put(MdcKeys.TRACE_ID, "caller");
        decorated.run();
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isEqualTo("caller");
    }

    @Test
    void taskFailureStillRestoresCallerContext() {
        MDC.put(MdcKeys.TRACE_ID, "parent");
        Runnable decorated = decorator.decorate(() -> {
            throw new IllegalStateException("boom");
        });
        MDC.put(MdcKeys.TRACE_ID, "caller");
        assertThatThrownBy(decorated::run).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isEqualTo("caller");
    }
}
