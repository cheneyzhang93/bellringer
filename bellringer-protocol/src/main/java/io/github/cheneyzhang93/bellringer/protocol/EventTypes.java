package io.github.cheneyzhang93.bellringer.protocol;

/**
 * 内置事件类型常量（F4 type 字段的公认取值，kebab-case；同时是控制台去重键前缀）。
 *
 * <p>自定义事件不受此清单限制，type 只要是 kebab-case 即可（见 {@link AlertEvent#TYPE_PATTERN}）。
 */
public final class EventTypes {

    /** 统一出口 ERROR 捕获。 */
    public static final String SYSTEM_ERROR = "system-error";

    /** 慢 SQL 拦截。 */
    public static final String SLOW_SQL = "slow-sql";

    /** 数据库锁超时。 */
    public static final String LOCK_TIMEOUT = "lock-timeout";

    /** 定时任务执行失败。 */
    public static final String SCHEDULED_TASK_ERROR = "scheduled-task-error";

    /** 健康自检（依赖 ping 等）。 */
    public static final String HEALTH_CHECK = "health-check";

    /** 访问日志（可关）。 */
    public static final String ACCESS_LOG = "access-log";

    private EventTypes() {
    }
}
