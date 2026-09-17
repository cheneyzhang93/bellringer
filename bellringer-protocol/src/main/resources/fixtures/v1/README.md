# v1 契约 fixtures（两仓共用）

本目录是 starter ↔ 控制台之间 v1 事件契约的唯一基准：OSS 仓契约测试直接加载；
控制台仓测试从 `bellringer-protocol` 构件 classpath 读取同一份文件（`/fixtures/v1/...`），
两仓不得各自维护副本。改动 fixture 前先确认对应接口是否处于冻结态（基线 F1–F10）。

| 文件 | 用途 |
|---|---|
| `event-request-full.json` | F1 全字段请求体，同时是规范化序列化基准 |
| `event-request-minimal.json` | 仅必填字段：缺失 level 视为 P2，其余可选字段为 null |
| `event-request-forward-compat.json` | 信封与事件均含未知字段：消费端容忍并丢弃（F4 只加不改不删） |
| `heartbeat-request.json` | F2 心跳请求体 |

规范化约定（fixtures 即基准）：

- `occurredAt` / `startedAt` 传输为 epoch millis（JSON number）；
- 可选字段为 null 时序列化省略（NON_NULL）；
- `sdkVersion` 位于信封层；心跳负载不含 sdkVersion（F2 字段清单中的 sdkVersion 由信封承载）；
- `level` 缺失视为 P2，再序列化时显式写出；
- 消费端必须关闭 `FAIL_ON_UNKNOWN_PROPERTIES`（Spring Boot 默认已关闭）；
- 未知 `level` 取值（枚举外）视为非法输入，解析即失败。
