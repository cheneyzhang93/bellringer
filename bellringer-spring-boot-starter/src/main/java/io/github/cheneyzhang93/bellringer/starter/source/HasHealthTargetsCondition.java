package io.github.cheneyzhang93.bellringer.starter.source;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 条件：{@code observability.sources.health-check.targets} 至少有一个非空 url。
 *
 * <p>健康自检默认 enabled 但目标清单缺省为空——"开了开关没配目标"必须静默跳过装配
 * （零线程零网络），因此用条件而不是在任务内部空转。
 */
public class HasHealthTargetsCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        List<BellringerProperties.Sources.HealthCheck.Target> targets;
        try {
            targets = Binder.get(context.getEnvironment())
                    .bind("observability.sources.health-check.targets",
                            Bindable.listOf(BellringerProperties.Sources.HealthCheck.Target.class))
                    .orElse(Collections.<BellringerProperties.Sources.HealthCheck.Target>emptyList());
        } catch (Exception e) {
            // 绑定失败的真实原因由 @ConfigurationProperties 绑定阶段暴露，这里不抢报
            return new ConditionOutcome(false, ConditionMessage.of("健康自检目标清单绑定失败: " + e.getMessage()));
        }
        int usable = 0;
        for (BellringerProperties.Sources.HealthCheck.Target target : targets) {
            if (target.getUrl() != null && !target.getUrl().trim().isEmpty()) {
                usable++;
            }
        }
        return new ConditionOutcome(usable > 0,
                ConditionMessage.of("健康自检可用目标数=" + usable + "/" + targets.size()));
    }
}
