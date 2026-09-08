# Transerver Development Log

## 2026-09-08 — 0.1.0 foundation

- 确定 Transerver 是多服务器节点网络，不是两个端点之间的同步工具。
- 选择单 Router、多节点的首版拓扑；Router 可嵌入 Minecraft 服务器。
- 外置 MySQL 从必要依赖中移除，本地原子文件信箱是默认持久化实现。
- 确定 `RELAYED → RECEIVED → APPLIED` 状态语义，最终回执来自目标节点。
- 确定网络层至少一次投递，上层使用 `messageId` 幂等处理。
- 开始纯 Java 21 核心、HMAC 请求认证和三节点集成测试。
- 明确可替换边界：Transport、MessageStore、Authenticator、RouteResolver 和 MessageHandler；不在公共协议中写死远仓或首版拓扑。
- 完成首批公共 API、带版本与 SHA-256 校验的二进制信封，以及 2 MiB 载荷上限。
- 完成本地原子文件存储、可持久化 Router、HMAC-SHA256 请求认证基础和节点投递循环。
- 三节点测试覆盖多目标路由、离线积压、目标节点重启去重、未知目标拒绝与 Router 重启恢复。
- 完成可替换的 HTTP `Transport`、HMAC 请求头验证和有界批量编码；三节点已通过真实 HTTP 套接字互发。
- 增加独立 Router 启动入口与 properties 配置模板，方便在不依赖 Minecraft 和外置数据库的情况下部署。
- 区分未知目标与暂时断网：未知目标产生最终 `REJECTED` 并继续处理队列，暂时故障保留 outbox 重试。
- 增加 `TranserverRuntime` 后台定时泵与立即唤醒；临时失败不会终止后续自动重试。
- 增加实现无关的 `NodeStatus`，为远仓监视器提供连接、队列、死信和处理状态。
- 增加交互式 probe 节点与三机手工测试说明；可以在不启动 Minecraft 的情况下验证真实网络、离线与重启恢复。
- 明确包裹只保存不可变 `nodeId`，IP、域名、端口和 Router URL 仅由 `RouteResolver` 在发送时解析；地址变更不会使旧包裹失效。
- 增加持久化发送结果队列；来源服重启后仍能完成退包、记账或其它最终回执动作。
- 建立 GitHub CI 与标签发布流程；每次提交自动执行 Java 21 构建和测试，标签产出可下载的发行包。
- 区分库版本、传输协议版本、存储格式版本和业务频道版本，避免未来扩展被单一版本号写死。
- 固化“稳定服务器身份、可变物理地址”规则：包裹仅保存 `nodeId`，发送时才由 `RouteResolver` 查找当前地址。
- 设计 Distant Stock 自定义路由组件与 Create `orderId` 的关联，替代按包裹地址推断回程服务器的 `ReturnRoute`。
- 细化配置 UI：本服别名与只读 `nodeId`、可变的 Router/传输地址、连接测试、已发现节点健康表；改地址只更新 `RouteResolver`，不改写包裹。
- 规定“重新生成身份”必须是单独的迁移操作并明确警告，因为它会让已有包裹指向不同节点；普通改名和改域名均不影响运输中的包裹。
- 实现可复用的 `NodeIdentity` 与原子身份文件：首次启动生成 UUID，别名可独立修改，并提供稳定的短识别码供配置界面展示。

### 从现有实现发现的问题

- Distant Stock 的跨服队列在内存中，重启会丢失。
- `/package` 在包裹只进入内存队列后便返回成功。
- 消息没有稳定 ID，模糊超时后的重试可能重复投递。
- `ReturnRoute` 在内存中按地址保存，重启和同名地址都会造成错误路由。
- 未知服务器 ID 会退回第一个 peer，不适合多服务器网络。
- 当前 NBT 和 HTTP 正文读取没有合理大小上限。
