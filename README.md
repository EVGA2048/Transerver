# Transerver

Transerver 是面向 Minecraft 多服务器网络的可靠消息前置库。它让任意服务器节点通过 Router 互发消息，并在断线、超时和进程重启后继续投递。

项目最初服务于 [Create: Distant Stock](https://github.com/EVGA2048/Create-Distant-Stock)：远仓负责解释 Create 包裹、订单和库存，Transerver 只负责服务器寻址、持久化传输、确认与去重。

## 设计目标

- 多服务器互通，不限制为两个固定端点；
- 不依赖外置 MySQL、Redis 或独立数据库；
- 消息先写入本地 outbox，再交给网络；
- Router 与接收端都先落盘再确认；
- 重试始终使用同一 `messageId`，接收端可以幂等处理；
- 纯 Java 21 核心，后续提供 NeoForge 和 Paper 适配层；
- 传输、存储、认证和路由都有接口；HTTP、文件信箱和单 Router 只是默认实现。

## 当前阶段

`0.1.0-SNAPSHOT` 已完成协议核心、文件信箱、可持久化 Router 和节点投递循环。三个节点互发、目标离线、接收端重启去重、未知目标拒绝及 Router 重启恢复已有自动测试。下一阶段是 HTTP 传输适配器和 Minecraft 运行时适配层。

设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，开发记录见 [DEVLOG.md](DEVLOG.md)。

## 构建

```bash
./gradlew test
./gradlew build
```

需要 JDK 21。
