package io.github.cheneyzhang93.bellringer.starter.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敲钟人 starter 配置（F8 冻结前缀 {@code observability.*}）。
 *
 * <p>总开关 {@code observability.enabled}（缺省 false）：关闭时零装配、零线程、零网络、零副作用。
 * 开启后各通道再按自身 {@code enabled}/参数完备性条件装配。
 */
@ConfigurationProperties(prefix = "observability")
public class BellringerProperties {

    /** 唯一总开关：false/缺省＝不装配任何组件。 */
    private boolean enabled = false;

    /** 统一应用标识（F5 取值链：本键 → spring.application.name → 启动失败）。 */
    private String app;

    /** 环境标识（本键 → spring.profiles.active 首个 → default）。 */
    private String env;

    private final Report report = new Report();

    private final Dedup dedup = new Dedup();

    private final Alert alert = new Alert();

    private final SlowSql slowSql = new SlowSql();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public Report getReport() {
        return report;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public Alert getAlert() {
        return alert;
    }

    public SlowSql getSlowSql() {
        return slowSql;
    }

    /** F1 事件上报出口（starter → 控制台 HTTP）。 */
    public static class Report {

        /** 上报模式：webhook＝仅本机直发 IM（兼容现状）；http＝仅上报控制台；both＝双出口。 */
        public enum Mode {
            WEBHOOK,
            HTTP,
            BOTH
        }

        private Mode mode = Mode.WEBHOOK;

        /** 控制台地址，如 http://console.internal:8080（mode 含 http 时必填）。 */
        private String endpoint = "";

        /** 租户 Token（控制台签发、可轮换；作为 Bearer 凭据）。 */
        private String token = "";

        /** 上报有界队列容量：满则丢弃并计数（绝不阻塞业务线程）。 */
        private int queueSize = 1024;

        /** 单次请求超时（毫秒）。 */
        private long timeoutMillis = 3000;

        /** 指数退避重试上限（次；仅上报出口有重试语义）。 */
        private int maxRetries = 5;

        /** 实例心跳周期（秒；F2 默认 30s，控制台 90s 判失联）。 */
        private int heartbeatIntervalSeconds = 30;

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }
    }

    /** F6 推送出口去重（上报出口全量不删，不受此配置影响）。 */
    public static class Dedup {

        private boolean enabled = true;

        /** 窗口（秒）：同 app+type+aggregateKey 窗口内只推送首条。 */
        private int windowSeconds = 300;

        /** Redis 键前缀：{prefix}:{app}:{type}:{aggregateKey}。 */
        private String keyPrefix = "obs:alert:dedup";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /** 推送出口与消息模板。 */
    public static class Alert {

        private final DingTalk dingtalk = new DingTalk();

        private final WeCom wecom = new WeCom();

        private final Feishu feishu = new Feishu();

        /** trace 检索跳转链接模板（含 {traceId} 占位；空＝不输出跳转行）。 */
        private String traceUrlTemplate = "";

        /** 无集中检索环境排障指引（含 {traceId} 占位；空＝不输出）。 */
        private String traceQueryHint = "";

        public DingTalk getDingtalk() {
            return dingtalk;
        }

        public WeCom getWecom() {
            return wecom;
        }

        public Feishu getFeishu() {
            return feishu;
        }

        public String getTraceUrlTemplate() {
            return traceUrlTemplate;
        }

        public void setTraceUrlTemplate(String traceUrlTemplate) {
            this.traceUrlTemplate = traceUrlTemplate;
        }

        public String getTraceQueryHint() {
            return traceQueryHint;
        }

        public void setTraceQueryHint(String traceQueryHint) {
            this.traceQueryHint = traceQueryHint;
        }

        /** 钉钉群机器人（OSS 通道）。 */
        public static class DingTalk {

            private boolean enabled = false;

            /** 群机器人 webhook 地址。 */
            private String webhook = "";

            /** 加签密钥（旧版自定义关键字机器人可留空）。 */
            private String secret = "";

            /** P1 级 @ 处理人手机号列表。 */
            private List<String> atMobiles = new ArrayList<String>();

            /** P0 是否 @所有人。 */
            private boolean atAll = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getWebhook() {
                return webhook;
            }

            public void setWebhook(String webhook) {
                this.webhook = webhook;
            }

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }

            public List<String> getAtMobiles() {
                return atMobiles;
            }

            public void setAtMobiles(List<String> atMobiles) {
                this.atMobiles = atMobiles;
            }

            public boolean isAtAll() {
                return atAll;
            }

            public void setAtAll(boolean atAll) {
                this.atAll = atAll;
            }
        }

        /** 企业微信群机器人（OSS 通道）。 */
        public static class WeCom {

            private boolean enabled = false;

            /** 群机器人 webhook 地址。 */
            private String webhook = "";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getWebhook() {
                return webhook;
            }

            public void setWebhook(String webhook) {
                this.webhook = webhook;
            }
        }

        /** 飞书群机器人（OSS 通道）。 */
        public static class Feishu {

            private boolean enabled = false;

            /** 群机器人 webhook 地址。 */
            private String webhook = "";

            /** 签名校验密钥（未开启签名校验可留空）。 */
            private String secret = "";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getWebhook() {
                return webhook;
            }

            public void setWebhook(String webhook) {
                this.webhook = webhook;
            }

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }
        }
    }

    /** 慢 SQL 事件源。 */
    public static class SlowSql {

        private boolean enabled = true;

        /** 慢 SQL 阈值（毫秒）：超过即发慢 SQL 事件。 */
        private long millis = 500;

        /** 升级阈值（毫秒）：超过直接升 P1，否则 P2。 */
        private long upgradeMillis = 3000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMillis() {
            return millis;
        }

        public void setMillis(long millis) {
            this.millis = millis;
        }

        public long getUpgradeMillis() {
            return upgradeMillis;
        }

        public void setUpgradeMillis(long upgradeMillis) {
            this.upgradeMillis = upgradeMillis;
        }
    }
}
