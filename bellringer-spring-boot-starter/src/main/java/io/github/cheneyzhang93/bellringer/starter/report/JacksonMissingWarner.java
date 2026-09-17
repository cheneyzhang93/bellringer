package io.github.cheneyzhang93.bellringer.starter.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * mode 含 http 但 classpath 缺 Jackson 时的显式告警（上报静默失效属生产事故，绝不静默）。
 */
public class JacksonMissingWarner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JacksonMissingWarner.class);

    @Override
    public void afterPropertiesSet() {
        log.error("敲钟人：observability.report.mode 含 http，但 classpath 缺少 jackson-databind，"
                + "事件上报与心跳不会生效——请引入 com.fasterxml.jackson.core:jackson-databind，或改回 mode=webhook");
    }
}
