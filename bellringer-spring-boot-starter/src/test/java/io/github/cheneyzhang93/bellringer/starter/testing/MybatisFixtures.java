package io.github.cheneyzhang93.bellringer.starter.testing;

import java.lang.reflect.Method;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/** 测试夹具：用真实 MyBatis 对象（非 Mockito）构造 MappedStatement 与 Executor 调用。 */
public final class MybatisFixtures {

    private MybatisFixtures() {
    }

    public static MappedStatement select(String id, String sql) {
        return statement(id, sql, SqlCommandType.SELECT);
    }

    public static MappedStatement update(String id, String sql) {
        return statement(id, sql, SqlCommandType.UPDATE);
    }

    public static MappedStatement statement(String id, String sql, SqlCommandType type) {
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new StaticSqlSource(configuration, sql);
        return new MappedStatement.Builder(configuration, id, sqlSource, type).build();
    }

    public static Invocation query(Executor target, MappedStatement statement, Object parameter) {
        return invocation(target, method("query", MappedStatement.class, Object.class, RowBounds.class,
                ResultHandler.class), statement, parameter);
    }

    public static Invocation update(Executor target, MappedStatement statement, Object parameter) {
        return invocation(target, method("update", MappedStatement.class, Object.class), statement, parameter);
    }

    private static Invocation invocation(Executor target, Method method, MappedStatement statement, Object parameter) {
        Object[] args = method.getParameterTypes().length == 4
                ? new Object[]{statement, parameter, RowBounds.DEFAULT, null}
                : new Object[]{statement, parameter};
        return new Invocation(target, method, args);
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return Executor.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("MyBatis Executor 缺少方法 " + name, e);
        }
    }
}
