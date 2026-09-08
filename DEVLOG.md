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

### 从现有实现发现的问题

- Distant Stock 的跨服队列在内存中，重启会丢失。
- `/package` 在包裹只进入内存队列后便返回成功。
- 消息没有稳定 ID，模糊超时后的重试可能重复投递。
- `ReturnRoute` 在内存中按地址保存，重启和同名地址都会造成错误路由。
- 未知服务器 ID 会退回第一个 peer，不适合多服务器网络。
- 当前 NBT 和 HTTP 正文读取没有合理大小上限。
