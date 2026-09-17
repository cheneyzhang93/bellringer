# Bellringer（敲钟人）

零基建告警套件 —— 应用内埋点直出事件源 ＋ 告警值班闭环。

面向没有可观测基建的团队：不装 Prometheus、不搭日志管道，接入 SDK 即有分级告警与值班闭环。配套控制台为独立闭源产品。

> 状态：重写开发中（跨仓契约 v1 已冻结）

## 模块

| 模块 | 说明 |
|---|---|
| `bellringer-protocol` | 跨仓事件契约：`AlertEvent` v1、枚举与传输信封。纯 Java 8，零框架依赖 |
| `bellringer-spring-boot-starter` | Spring Boot 2.7 自动装配（Java 8 兼容）：事件源采集（ERROR 统一出口 / 慢 SQL / 锁超时 / 定时任务失败 / 健康自检）与告警出口 |

## 构建

需要 JDK 8+ 与 Maven 3.6+：

```bash
mvn verify
```

## License

[Apache License 2.0](LICENSE)
