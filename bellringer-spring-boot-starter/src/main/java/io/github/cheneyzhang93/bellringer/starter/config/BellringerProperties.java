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

    /** 应用版本（F2 心跳 version 来源；空＝回退 spring.application.version，仍空则心跳不带）。 */
    private String version;

    private final Report report = new Report();

    private final Dedup dedup = new Dedup();

    private final Alert alert = new Alert();

    private final SlowSql slowSql = new SlowSql();

    private final Sources sources = new Sources();

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public Sources getSources() {
        return sources;
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

        /**
         * 多实例去重：true 且容器内存在 {@code StringRedisTemplate} 时启用 Redis 窗口去重；
         * 缺省 false＝单实例内存去重（零基建部署）。
         */
        private boolean redis = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRedis() {
            return redis;
        }

        public void setRedis(boolean redis) {
            this.redis = redis;
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

        /** webhook 连接超时（毫秒）。 */
        private int connectTimeoutMillis = 3000;

        /** webhook 读取超时（毫秒）。 */
        private int readTimeoutMillis = 5000;

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

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

    /** S3 事件源开关族（observability.sources.*，F8 前缀内的扩展键；总开关仍为 observability.enabled）。 */
    public static class Sources {

        private final ErrorLog errorLog = new ErrorLog();

        private final LockTimeout lockTimeout = new LockTimeout();

        private final ScheduledTask scheduledTask = new ScheduledTask();

        private final HealthCheck healthCheck = new HealthCheck();

        private final AccessLog accessLog = new AccessLog();

        public ErrorLog getErrorLog() {
            return errorLog;
        }

        public LockTimeout getLockTimeout() {
            return lockTimeout;
        }

        public ScheduledTask getScheduledTask() {
            return scheduledTask;
        }

        public HealthCheck getHealthCheck() {
            return healthCheck;
        }

        public AccessLog getAccessLog() {
            return accessLog;
        }

        /** ERROR 统一出口：日志框架 ERROR 级事件 → system-error（根因类|抛出点 聚合去重）。 */
        public static class ErrorLog {

            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /** 数据库锁超时 / 死锁事件源（MyBatis 拦截，异常识别，不改写 SQL 行为）。 */
        public static class LockTimeout {

            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /** 定时任务失败事件源（Boot taskScheduler 错误处理包装，不抢占宿主调度器）。 */
        public static class ScheduledTask {

            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /** 健康自检：周期探测配置目标，连续失败达阈值 → health-check 事件。 */
        public static class HealthCheck {

            private boolean enabled = true;

            /** 探测周期（秒）。 */
            private int intervalSeconds = 60;

            /** 连续失败几次才发事件（抖动抑制）。 */
            private int failureThreshold = 3;

            /** 首次探测延迟（秒；避开启动风暴）。 */
            private int initialDelaySeconds = 10;

            private List<Target> targets = new ArrayList<Target>();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getIntervalSeconds() {
                return intervalSeconds;
            }

            public void setIntervalSeconds(int intervalSeconds) {
                this.intervalSeconds = intervalSeconds;
            }

            public int getFailureThreshold() {
                return failureThreshold;
            }

            public void setFailureThreshold(int failureThreshold) {
                this.failureThreshold = failureThreshold;
            }

            public int getInitialDelaySeconds() {
                return initialDelaySeconds;
            }

            public void setInitialDelaySeconds(int initialDelaySeconds) {
                this.initialDelaySeconds = initialDelaySeconds;
            }

            public List<Target> getTargets() {
                return targets;
            }

            public void setTargets(List<Target> targets) {
                this.targets = targets;
            }

            /** 单个探测目标（HTTP GET，2xx/3xx 视为存活）。 */
            public static class Target {

                private String name = "";

                private String url = "";

                private int timeoutMillis = 3000;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                public String getUrl() {
                    return url;
                }

                public void setUrl(String url) {
                    this.url = url;
                }

                public int getTimeoutMillis() {
                    return timeoutMillis;
                }

                public void setTimeoutMillis(int timeoutMillis) {
                    this.timeoutMillis = timeoutMillis;
                }
            }
        }

        /** 访问日志：默认关闭；开启后每请求一条 INFO，另对 5xx / 慢请求发事件（不采集报文体）。 */
        public static class AccessLog {

            private boolean enabled = false;

            /** 慢请求阈值（毫秒），超阈值发 P2 事件。 */
            private long slowMillis = 1000;

            /** 5xx 响应是否发事件（P1）。 */
            private boolean emitServerErrors = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public long getSlowMillis() {
                return slowMillis;
            }

            public void setSlowMillis(long slowMillis) {
                this.slowMillis = slowMillis;
            }

            public boolean isEmitServerErrors() {
                return emitServerErrors;
            }

            public void setEmitServerErrors(boolean emitServerErrors) {
                this.emitServerErrors = emitServerErrors;
            }
        }
    }
}
