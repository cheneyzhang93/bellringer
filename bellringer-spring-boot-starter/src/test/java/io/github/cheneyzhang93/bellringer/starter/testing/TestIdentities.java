package io.github.cheneyzhang93.bellringer.starter.testing;

import io.github.cheneyzhang93.bellringer.starter.config.BellringerProperties;
import io.github.cheneyzhang93.bellringer.starter.core.AppIdentity;
import org.springframework.core.env.StandardEnvironment;

/** 测试夹具：构造真实 {@link AppIdentity}（走 F5 公开取值链，不碰包内构造器）。 */
public final class TestIdentities {

    private TestIdentities() {
    }

    public static AppIdentity app(String app) {
        BellringerProperties properties = new BellringerProperties();
        properties.setApp(app);
        properties.setEnv("test");
        return AppIdentity.resolve(properties, new StandardEnvironment());
    }
}
