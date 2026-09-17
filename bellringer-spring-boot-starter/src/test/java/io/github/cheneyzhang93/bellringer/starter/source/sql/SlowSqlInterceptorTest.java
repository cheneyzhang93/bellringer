package io.github.cheneyzhang93.bellringer.starter.source.sql;

import io.github.cheneyzhang93.bellringer.protocol.AlertEvent;
import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.testing.MybatisFixtures;
import io.github.cheneyzhang93.bellringer.starter.testing.RecordingSink;
import io.github.cheneyzhang93.bellringer.starter.testing.TestIdentities;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
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
 * 慢 SQL 事件源验收：阈值分级、指纹聚合、参数不外泄、异常路径不重复上报。
 */
class SlowSqlInterceptorTest {

    private final RecordingSink sink = new RecordingSink();

    private final BellringerProperties properties = new BellringerProperties();

    @Test
    void fastQueryEmitsNothing() throws Throwable {
        properties.getSlowSql().setMillis(1000L);
        SlowSqlInterceptor interceptor = newInterceptor();
        Invocation invocation = MybatisFixtures.query(mock(Executor.class),
                MybatisFixtures.select("com.example.DemoMapper.selectOrders", "select * from orders where id = ?"), null);

        interceptor.intercept(invocation);

        assertThat(sink.count()).isZero();
    }

    @Test
    void slowQueryEmitsP2WithFingerprintAndNoParameters() throws Throwable {
        properties.getSlowSql().setMillis(10L);
        properties.getSlowSql().setUpgradeMillis(1000L);
        SlowSqlInterceptor interceptor = newInterceptor();
        Map<String, Object> parameter = new HashMap<String, Object>();
        parameter.put("phone", "13800000000");
        Invocation invocation = slowQuery(50L, parameter);

        interceptor.intercept(invocation);

        AlertEvent event = sink.first();
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(EventTypes.SLOW_SQL);
        assertThat(event.getLevel()).isEqualTo(AlertLevel.P2);
        assertThat(event.getAggregateKey())
                .isEqualTo("com.example.DemoMapper.selectOrders|select * from orders where id = ?");
        assertThat(event.getSource()).isEqualTo("com.example.DemoMapper.selectOrders");
        assertThat(event.getTitle()).contains("selectOrders");
        assertThat(event.getMessage()).contains("指纹：select * from orders where id = ?");
        // 只带指纹与耗时，参数值绝不进事件
        assertThat(event.getMessage()).doesNotContain("13800000000");
    }

    @Test
    void overUpgradeThresholdEmitsP1() throws Throwable {
        properties.getSlowSql().setMillis(10L);
        properties.getSlowSql().setUpgradeMillis(20L);
        SlowSqlInterceptor interceptor = newInterceptor();

        interceptor.intercept(slowQuery(50L, null));

        assertThat(sink.first().getLevel()).isEqualTo(AlertLevel.P1);
    }

    @Test
    void slowUpdateStatementIsAlsoCaptured() throws Throwable {
        properties.getSlowSql().setMillis(10L);
        SlowSqlInterceptor interceptor = newInterceptor();
        Executor executor = mock(Executor.class);
        when(executor.update(any(MappedStatement.class), any())).thenAnswer(invocation -> {
            Thread.sleep(40L);
            return 1;
        });
        MappedStatement statement = MybatisFixtures.update("com.example.DemoMapper.touchOrder",
                "update orders set updated_at = ? where id = ?");

        interceptor.intercept(MybatisFixtures.update(executor, statement, null));

        assertThat(sink.first().getAggregateKey())
                .isEqualTo("com.example.DemoMapper.touchOrder|update orders set updated_at = ? where id = ?");
    }

    @Test
    void sqlExceptionPropagatesWithoutSlowEvent() throws Throwable {
        properties.getSlowSql().setMillis(1L);
        SlowSqlInterceptor interceptor = newInterceptor();
        Executor executor = mock(Executor.class);
        when(executor.query(any(MappedStatement.class), any(), any(), any()))
                .thenThrow(new SQLException("connection reset"));
        Invocation invocation = MybatisFixtures.query(executor,
                MybatisFixtures.select("com.example.DemoMapper.selectOrders", "select * from orders"), null);

        Throwable thrown = catchThrowable(() -> interceptor.intercept(invocation));

        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        assertThat(thrown.getCause()).isInstanceOf(SQLException.class);
        // 异常路径归错误源/锁超时源，慢 SQL 不重复上报
        assertThat(sink.count()).isZero();
    }

    private SlowSqlInterceptor newInterceptor() {
        return new SlowSqlInterceptor(properties, new AlertEventFactory(TestIdentities.app("demo")), sink);
    }

    private static Invocation slowQuery(long sleepMillis, Object parameter) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.query(any(MappedStatement.class), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(sleepMillis);
                    return null;
                });
        return MybatisFixtures.query(executor,
                MybatisFixtures.select("com.example.DemoMapper.selectOrders", "select * from orders where id = 42"),
                parameter);
    }
}
