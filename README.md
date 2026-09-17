# Bellringer（敲钟人）

零基建告警套件 —— 应用内埋点直出事件源 ＋ 告警值班闭环。

面向没有可观测基建的团队：不装 Prometheus、不搭日志管道，引入一个依赖、写一段配置，即有分级告警、降噪聚合与值班闭环。配套控制台（值班排班、认领、升级、电话）为独立产品，通过 HTTP 契约与本 SDK 解耦对接。

[![CI](https://github.com/cheneyzhang93/bellringer/actions/workflows/ci.yml/badge.svg)](https://github.com/cheneyzhang93/bellringer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> 状态：0.1.0-SNAPSHOT 开发中（跨仓契约 v1 已冻结，尚未发布到 Maven Central）

## 模块

| 构件 | 说明 |
|---|---|
| `bellringer-protocol` | 跨仓事件契约 v1：`AlertEvent`、`AlertLevel`、`EventTypes`、`InstanceIds`、上报/心跳信封（F1/F2）、v1 fixtures。纯 Java 8、零框架依赖 |
| `bellringer-spring-boot-starter` | Spring Boot 2.7 自动装配（Java 8）：事件源采集（ERROR 出口 / 慢 SQL / 锁超时 / 定时任务 / 健康自检 / 访问日志）与告警出口（三家群机器人 / 控制台上报 / 心跳） |

## 环境要求

- JDK 8+（构件以 `source/target 1.8` 编译，CI 以 JDK 8 实机验证）
- Spring Boot 2.7.x（javax 体系）
- 可选依赖按需生效：MyBatis（慢 SQL / 锁超时）、Logback（ERROR 出口）、`spring-data-redis`（多实例去重）、Jackson（控制台上报）

## 快速开始

```xml
<dependency>
  <groupId>io.github.cheneyzhang93</groupId>
  <artifactId>bellringer-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

> 未发布前请从源码构建：`mvn install`。

```yaml
observability:
  enabled: true
  app: payments-api          # 缺省回退 spring.application.name；两者都缺＝启动失败
  alert:
    dingtalk:
      enabled: true
      webhook: https://oapi.dingtalk.com/robot/send?access_token=xxx
```

启动后应用中任意 `log.error(...)`、超阈值的 SQL、锁等待超时等都会变成分级告警推送到群里；什么都不配时零装配、零线程、零网络。

## 事件源

| type | 来源 | 级别 | 缺省 |
|---|---|---|---|
| `system-error` | 日志框架 ERROR 统一出口（自动跳过本框架自身 logger 防回环） | P1 | 开 |
| `slow-sql` | MyBatis 慢 SQL 拦截（SQL 指纹归一化聚合，不带参数值） | 超升级阈值 P1，否则 P2 | 开 |
| `lock-timeout` | 数据库锁等待超时 / 死锁识别 | P2 | 开 |
| `scheduled-task-error` | Boot `taskScheduler` 执行失败（调度不中断，继续后续触发） | P1 | 开 |
| `health-check` | 周期 HTTP 探测配置目标，连续失败达阈值才发事件，恢复发 P2 | P1 | 开（目标为空则不装配） |
| `access-log` | 访问日志：5xx 与慢请求（路径归一化，数字/UUID 段折叠为 `{id}`） | 5xx P1 / 慢请求 P2 | 关 |

自定义事件：`AlertEventFactory` 构造 `AlertEvent` 后交给 `AlertEventSink` 即可复用完整管道（去重、渲染、推送、上报）。

## 出口

- **群机器人 webhook**（OSS 通道）：钉钉（加签/@ 全员/@ 手机号）、企业微信、飞书（签名校验）三家可并存，同一条告警全量发送
- **日志兜底**：未装配任何其他出口时，告警落 ERROR 日志、不触网
- **控制台上报**：`report.mode=http/both` 时上报全量事件并周期发送实例心跳（缺省 30s，控制台 90s 判失联）

## 语义与降噪

- **推送去重**：同 `app + type + aggregateKey` 在窗口期（缺省 300s）内只推送首条；单实例内存去重，多实例可切 Redis（`dedup.redis=true`）
- **上报全量**：去重只影响推送出口；控制台上报始终全量，保证值班视图与报表完整
- **失败语义**：webhook 推送失败＝单次尝试＋日志，不重试；控制台上报失败＝指数退避重试（`report.max-retries`）
- **不阻塞业务**：渲染与推送在专用线程池完成，有界队列满则丢弃并计数；事件源本身只做轻量采集，异常一律吞掉不影响宿主

## 配置

全量键与缺省值见 [`example.yml`](example.yml)（F8 冻结前缀 `observability.*`）。

集成方式：引入 starter → 打开 `observability.enabled` → 按需开启通道。宿主 Bean 优先——所有内置组件（出口、去重器、渲染器等）均 `@ConditionalOnMissingBean`，可整体替换。

## 扩展点

实现 `AlertSender` 并注册为 Spring Bean，即被推送出口复合收集：

```java
public class SmsAlertSender implements AlertSender {

    @Override
    public String name() {
        return "sms";
    }

    @Override
    public void send(AlertEvent event, String markdown) throws Exception {
        // 自定义通道：短信网关、自建服务等
    }
}
```

约定：推送失败单次尝试、由出口统一记录现场，不重试。

## 构建

需要 JDK 8+ 与 Maven 3.6+：

```bash
mvn verify
```

## 发布（维护者）

1. 前置：Sonatype Central 账号（发布令牌写入 `~/.m2/settings.xml` 的 `central` server）＋ GPG 签名密钥
2. 版本去 `-SNAPSHOT`，`mvn -Prelease deploy`：生成 sources/javadoc 构件、GPG 签名并上传 Central Portal（默认不自动发布，在 Portal 确认后点 Publish）
3. 打 `v*` 标签推送后，`release.yml` 在 CI 上自动完成同样流程并创建 GitHub Release（需配置仓库 secrets：`CENTRAL_USERNAME`、`CENTRAL_PASSWORD`、`GPG_PRIVATE_KEY`、`GPG_PASSPHRASE`）

## License

[Apache License 2.0](LICENSE)
