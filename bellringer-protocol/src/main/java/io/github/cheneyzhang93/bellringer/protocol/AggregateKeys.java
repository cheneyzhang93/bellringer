package io.github.cheneyzhang93.bellringer.protocol;

/**
 * aggregateKey 约定（F4 补充，跨仓唯一事实来源）——控制台据此把「故障」与「恢复」归入同一 incident。
 *
 * <p>规则：
 * <ul>
 *   <li>故障信号＝{@link #down(String)} 生成（{@code down|{subject}}）；</li>
 *   <li>恢复信号＝{@link #up(String)} 生成（{@code up|{subject}}）；</li>
 *   <li>两者共享同一 subject，控制台指纹＝{@code {type}|{subject}}，恢复信号用于 resolved 对应 incident；</li>
 *   <li>无前缀的 aggregateKey（如慢 SQL 的 {@code statementId|fingerprint}）按原样参与聚合，
 *       不产生恢复信号（闭环靠人工关闭或后续切片策略）。</li>
 * </ul>
 *
 * <p>本类为零依赖纯 J8 常量工具，生产侧（starter 事件源）与消费侧（控制台）必须共用，
 * 不得各自硬编码前缀字面量。
 */
public final class AggregateKeys {

    /** 故障信号前缀（{@code down|}）。 */
    public static final String DOWN_PREFIX = "down|";

    /** 恢复信号前缀（{@code up|}）。 */
    public static final String UP_PREFIX = "up|";

    /** 生成故障信号聚合键。 */
    public static String down(String subject) {
        return DOWN_PREFIX + subject;
    }

    /** 生成恢复信号聚合键。 */
    public static String up(String subject) {
        return UP_PREFIX + subject;
    }

    /** 是否恢复信号（{@code up|} 前缀）。 */
    public static boolean isRecovery(String aggregateKey) {
        return aggregateKey != null && aggregateKey.startsWith(UP_PREFIX);
    }

    /** 是否故障信号（{@code down|} 前缀）。 */
    public static boolean isFailure(String aggregateKey) {
        return aggregateKey != null && aggregateKey.startsWith(DOWN_PREFIX);
    }

    /** 取主体（剥掉 {@code down|}/{@code up|} 前缀；无前缀原样返回；null → null）。 */
    public static String subject(String aggregateKey) {
        if (aggregateKey == null) {
            return null;
        }
        if (aggregateKey.startsWith(DOWN_PREFIX)) {
            return aggregateKey.substring(DOWN_PREFIX.length());
        }
        if (aggregateKey.startsWith(UP_PREFIX)) {
            return aggregateKey.substring(UP_PREFIX.length());
        }
        return aggregateKey;
    }

    private AggregateKeys() {
    }
}
