package io.github.cheneyzhang93.bellringer.starter.source.sql;

import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 慢 SQL 事件源（MyBatis 拦截 {@code Executor.query/update}，只观测不改写执行路径）。
 *
 * <p>阈值（F8：{@code observability.slow-sql.millis}）超限发 P2；超升级阈值
 * （{@code upgrade-millis}）直接升 P1。聚合键＝{@code statementId|SQL 指纹}；
 * 详情只带指纹与耗时，<b>绝不带参数值</b>（防敏感数据外泄）。执行异常不归本源（异常由错误源与锁超时源处理）。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    private final BellringerProperties properties;

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    public SlowSqlInterceptor(BellringerProperties properties, AlertEventFactory events, AlertEventSink sink) {
        this.properties = properties;
        this.events = events;
        this.sink = sink;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable t) {
            throw t; // 异常路径交由错误源/锁超时源，慢 SQL 不重复上报
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        long threshold = properties.getSlowSql().getMillis();
        if (millis >= threshold) {
            emit(invocation, millis);
        }
        return result;
    }

    private void emit(Invocation invocation, long millis) {
        try {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            String sql = boundSql(invocation, statement).getSql();
            String fingerprint = SqlFingerprint.of(sql);
            boolean upgraded = millis >= properties.getSlowSql().getUpgradeMillis();
            sink.emit(events.builder(EventTypes.SLOW_SQL)
                    .level(upgraded ? AlertLevel.P1 : AlertLevel.P2)
                    .title("慢 SQL（" + millis + "ms）：" + shortId(statement.getId()))
                    .message("耗时 " + millis + "ms（阈值 " + properties.getSlowSql().getMillis() + "ms）\n"
                            + "语句：" + statement.getId() + "\n"
                            + "指纹：" + fingerprint)
                    .source(statement.getId())
                    .aggregateKey(statement.getId() + "|" + fingerprint)
                    .build());
        } catch (Exception e) {
            log.warn("[慢 SQL] 事件构造失败（不影响业务）: {}", e.toString());
        }
    }

    private static BoundSql boundSql(Invocation invocation, MappedStatement statement) {
        Object[] args = invocation.getArgs();
        if (args.length == 6 && args[5] instanceof BoundSql) {
            return (BoundSql) args[5];
        }
        Object parameter = args.length > 1 ? args[1] : null;
        return statement.getBoundSql(parameter);
    }

    private static String shortId(String statementId) {
        if (statementId == null) {
            return "unknown";
        }
        int dot = statementId.lastIndexOf('.');
        return dot < 0 ? statementId : statementId.substring(dot + 1);
    }
}
