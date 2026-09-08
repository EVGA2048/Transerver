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

`0.1.0-SNAPSHOT` 已完成协议核心、文件信箱、可持久化 Router、HTTP 传输、自动重试运行时和节点状态快照。三个节点互发、目标离线、接收端重启去重、未知目标拒绝及 Router 重启恢复已有自动测试。下一阶段是 Minecraft 运行时适配层。

设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，开发记录见 [DEVLOG.md](DEVLOG.md)。

## 构建

```bash
./gradlew test
./gradlew build
```

需要 JDK 21。

## 启动 Router

复制 `transerver-router.example.properties` 为 `transerver-router.properties`，填写所有服务器的稳定 ID，并将示例密钥替换为至少 32 字节的随机密钥。然后运行：

```bash
./gradlew run
```

正式跨公网使用时应在 Router 前配置 TLS 反向代理或受控隧道。HMAC 用来验证服务器身份，不负责加密网络内容。
