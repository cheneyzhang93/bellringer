package io.github.cheneyzhang93.bellringer.starter.source.sql;

import io.github.cheneyzhang93.bellringer.protocol.AlertLevel;
import io.github.cheneyzhang93.bellringer.protocol.EventTypes;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventFactory;
import io.github.cheneyzhang93.bellringer.starter.core.AlertEventSink;
import java.sql.SQLException;
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
 * 锁超时 / 死锁事件源（MyBatis 拦截，异常识别，<b>不改变执行与异常语义</b>：原异常原样抛出）。
 *
 * <p>识别口径：MySQL 1205 锁等待超时＝P2；MySQL 1213 死锁、SQLState 40001（序列化失败/死锁）、
 * 40P01（PostgreSQL 死锁）＝P1。聚合键＝{@code statementId|SQL 指纹}，同类锁冲突窗口内只推首条。
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
public class LockTimeoutInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(LockTimeoutInterceptor.class);

    /** MySQL 锁等待超时。 */
    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

    /** MySQL 死锁。 */
    private static final int MYSQL_DEADLOCK = 1213;

    private final AlertEventFactory events;

    private final AlertEventSink sink;

    public LockTimeoutInterceptor(AlertEventFactory events, AlertEventSink sink) {
        this.events = events;
        this.sink = sink;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (Throwable t) {
            LockKind kind = classify(t);
            if (kind != null) {
                emit(invocation, t, kind);
            }
            throw t;
        }
    }

    /** 沿 cause 链识别锁冲突；非锁冲突返回 null。 */
    static LockKind classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            LockKind kind = classifySingle(current);
            if (kind != null) {
                return kind;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    private static LockKind classifySingle(Throwable throwable) {
        if (throwable instanceof SQLException) {
            SQLException sqlException = (SQLException) throwable;
            if (sqlException.getErrorCode() == MYSQL_DEADLOCK) {
                return LockKind.DEADLOCK;
            }
            if (sqlException.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT) {
                return LockKind.WAIT_TIMEOUT;
            }
            String sqlState = sqlException.getSQLState();
            if ("40001".equals(sqlState) || "40P01".equals(sqlState)) {
                return LockKind.DEADLOCK;
            }
        }
        String message = throwable.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("deadlock")) {
                return LockKind.DEADLOCK;
            }
            if (lower.contains("lock wait timeout") || lower.contains("锁等待超时")) {
                return LockKind.WAIT_TIMEOUT;
            }
        }
        return null;
    }

    private void emit(Invocation invocation, Throwable throwable, LockKind kind) {
        try {
            String statementId = statementId(invocation);
            String fingerprint = fingerprint(invocation);
            sink.emit(events.builder(EventTypes.LOCK_TIMEOUT)
                    .level(kind.getLevel())
                    .title(kind.getLabel() + "：" + shortId(statementId))
                    .message("数据库锁冲突（" + kind.getLabel() + "）\n"
                            + "语句：" + statementId + "\n"
                            + "指纹：" + fingerprint + "\n"
                            + "原因：" + throwable + "\n"
                            + "排查提示：检查同表并发事务的加锁顺序与索引命中情况（未命中索引会放大锁范围）。")
                    .source(statementId)
                    .aggregateKey(statementId + "|" + fingerprint)
                    .build());
        } catch (Exception e) {
            log.warn("[锁超时] 事件构造失败（不影响业务）: {}", e.toString());
        }
    }

    private static String statementId(Invocation invocation) {
        Object first = invocation.getArgs()[0];
        return first instanceof MappedStatement ? ((MappedStatement) first).getId() : "unknown-statement";
    }

    /** 尽力取 SQL 指纹；取不到（动态 SQL 参数不匹配等）退化为 unknown-sql，绝不因取指纹影响异常语义。 */
    private static String fingerprint(Invocation invocation) {
        Object[] args = invocation.getArgs();
        Object first = args[0];
        if (!(first instanceof MappedStatement)) {
            return "unknown-sql";
        }
        MappedStatement statement = (MappedStatement) first;
        try {
            BoundSql boundSql;
            if (args.length == 6 && args[5] instanceof BoundSql) {
                boundSql = (BoundSql) args[5];
            } else {
                boundSql = statement.getBoundSql(args.length > 1 ? args[1] : null);
            }
            return SqlFingerprint.of(boundSql.getSql());
        } catch (Exception e) {
            return "unknown-sql";
        }
    }

    private static String shortId(String statementId) {
        if (statementId == null) {
            return "unknown";
        }
        int dot = statementId.lastIndexOf('.');
        return dot < 0 ? statementId : statementId.substring(dot + 1);
    }

    /** 锁冲突种类（决定级别与文案）。 */
    enum LockKind {

        /** 锁等待超时：多为瞬态，P2。 */
        WAIT_TIMEOUT(AlertLevel.P2, "锁等待超时"),

        /** 死锁：多与加锁顺序/索引缺失相关，需人工介入，P1。 */
        DEADLOCK(AlertLevel.P1, "数据库死锁");

        private final AlertLevel level;

        private final String label;

        LockKind(AlertLevel level, String label) {
            this.level = level;
            this.label = label;
        }

        AlertLevel getLevel() {
            return level;
        }

        String getLabel() {
            return label;
        }
    }
}
