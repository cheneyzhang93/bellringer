package io.github.cheneyzhang93.bellringer.starter.source.sql;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.MybatisFixtures;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import java.sql.SQLException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 锁超时/死锁事件源验收：错误码与 SQLState 识别、级别分级、非锁冲突静默透传、异常语义不变。
 */
class LockTimeoutInterceptorTest {

    private static final String STATEMENT_ID = "com.example.DemoMapper.lockOrder";

    private static final String SQL = "update orders set status = ? where id = ?";

    private final RecordingSink sink = new RecordingSink();

    private final LockTimeoutInterceptor interceptor =
            new LockTimeoutInterceptor(new AlertEventFactory(TestIdentities.app("demo")), sink);

    @Test
    void mysql1205EmitsP2LockWaitTimeout() throws Throwable {
        intercept(new SQLException("Lock wait timeout exceeded; try restarting transaction",
                "HY000", 1205));

        AlertEvent event = sink.first();
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(EventTypes.LOCK_TIMEOUT);
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P2);
        assertThat(event.getTitle()).contains("锁等待超时").contains("lockOrder");
        assertThat(event.getAggregateKey())
                .isEqualTo(STATEMENT_ID + "|update orders set status = ? where id = ?");
        assertThat(event.getMessage()).contains("排查提示");
    }

    @Test
    void mysql1213EmitsP1Deadlock() throws Throwable {
        intercept(new SQLException("Deadlock found when trying to get lock", "40001", 1213));

        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
        assertThat(sink.first().getTitle()).contains("死锁");
    }

    @Test
    void sqlStateOnlyDeadlockIsDetected() throws Throwable {
        intercept(new SQLException("serialization failure", "40001", 0));

        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
    }

    @Test
    void messageBasedFallbackDetectsLockConflict() throws Throwable {
        intercept(new SQLException("Deadlock found when trying to get lock"));

        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
    }

    @Test
    void nestedCauseIsInspected() throws Throwable {
        intercept(new IllegalStateException("批量更新失败",
                new SQLException("Lock wait timeout exceeded", "HY000", 1205)));

        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P2);
        assertThat(sink.first().getMessage()).contains("锁等待超时");
    }

    @Test
    void nonLockExceptionPropagatesSilently() throws Throwable {
        SQLException duplicateKey = new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062);
        Invocation invocation = throwingQuery(duplicateKey);

        Throwable thrown = catchThrowable(() -> interceptor.intercept(invocation));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCause()).isSameAs(duplicateKey);
        assertThat(sink.count()).isZero();
    }

    @Test
    void interceptorNeverSwallowsException() throws Throwable {
        Invocation invocation = throwingQuery(new SQLException("Lock wait timeout exceeded", "HY000", 1205));

        Throwable thrown = catchThrowable(() -> interceptor.intercept(invocation));

        // 事件照发，异常照抛：观测不改变执行语义
        assertThat(thrown).isNotNull();
        assertThat(sink.count()).isEqualTo(1);
    }

    /** 触发一次会抛异常的拦截（异常语义由专测覆盖，这里只关心事件）。 */
    private void intercept(Throwable failure) {
        catchThrowable(() -> interceptor.intercept(throwingQuery(failure)));
    }

    private static Invocation throwingQuery(Throwable throwable) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.query(any(MappedStatement.class), any(), any(), any())).thenThrow(throwable);
        return MybatisFixtures.query(executor, MybatisFixtures.select(STATEMENT_ID, SQL), null);
    }
}
