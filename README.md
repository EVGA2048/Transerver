# Transerver

Transerver 是面向 Minecraft 多服务器网络的可靠消息前置库。它让任意服务器节点通过 Router 互发消息，并在断线、超时和进程重启后继续投递。

项目最初服务于 [Create: Distant Stock](https://github.com/EVGA2048/Create-Distant-Stock)：远仓负责解释 Create 包裹、订单和库存，Transerver 只负责服务器寻址、持久化传输、确认与去重。

## 设计目标

- 多服务器互通，不限制为两个固定端点；
- 不依赖外置 MySQL、Redis 或独立数据库；
- 消息先写入本地 outbox，再交给网络；
- Router 与接收端都先落盘再确认；
- 重试始终使用同一 `messageId`，接收端可以幂等处理；
- 纯 Java 21 核心，并提供独立的 NeoForge 1.21.1 前置模组入口；
- 传输、存储、认证和路由都有接口；HTTP、文件信箱和单 Router 只是默认实现。

## 当前阶段

`0.1.0-SNAPSHOT` 已完成协议核心、文件信箱、可持久化 Router、HTTP 传输、自动重试运行时、节点状态快照和 NeoForge 服务端生命周期接入。三个节点互发、目标离线、接收端重启去重、未知目标拒绝及 Router 重启恢复已有自动测试。下一阶段是节点发现、管理界面和 Distant Stock 业务频道接入。

总体设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，远仓接入约定见 [docs/DISTANTSTOCK-INTEGRATION.md](docs/DISTANTSTOCK-INTEGRATION.md)，兼容策略见 [docs/VERSIONING.md](docs/VERSIONING.md)，开发记录见 [DEVLOG.md](DEVLOG.md)。

## 构建

```bash
./gradlew test
./gradlew build
```

需要 JDK 21。

构建产物 `build/libs/Transerver-<version>.jar` 是双用途发行文件：

- 放入 NeoForge 服务器的 `mods` 目录时，它作为独立前置模组加载；
- 使用 `java -jar Transerver-<version>.jar [router.properties]` 时，它作为外置 Router 运行。

Transerver 与 Distant Stock 分别发布。Distant Stock 声明 Transerver 为服务端必要依赖，不把它内嵌进自己的 JAR。

## NeoForge 节点配置

首次安装后启动一次服务器，编辑世界的 `serverconfig/transerver-server.toml`：

```toml
enabled = true
nodeAlias = "生存服"
routerUrl = "http://127.0.0.1:8765/"
networkSecret = "替换成至少32字节的随机密钥"
pollMillis = 500
```

稳定 UUID 保存在服务器根目录的 `transerver/node-identity.properties`。它应随服务器一起备份；修改别名、Router IP 或域名都不会改变该 UUID。默认 `enabled = false`，未完成配置时不会启动网络运行时。

管理员命令：

- `/transerver identity`：显示服务器别名、短识别码和完整 UUID；
- `/transerver status`：显示 Router 连通状态、各持久队列深度和最近错误。

## 启动 Router

复制 `transerver-router.example.properties` 为 `transerver-router.properties`，填写所有服务器的稳定 ID，并将示例密钥替换为至少 32 字节的随机密钥。然后运行：

```bash
java -jar Transerver-0.1.0.jar transerver-router.properties
```

正式跨公网使用时应在 Router 前配置 TLS 反向代理或受控隧道。HMAC 用来验证服务器身份，不负责加密网络内容。

## 多节点手工测试

发行包内置交互式 probe 节点，可在接入 Minecraft 前用三台机器验证路由、断线积压、重启恢复和最终回执。步骤见 [docs/TESTING.md](docs/TESTING.md)。
