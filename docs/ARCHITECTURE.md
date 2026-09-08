# Transerver Architecture

## 1. 边界

Transerver 传递带频道名称的二进制消息，不解析 Minecraft 物品、NBT、仓储网络或游戏规则。

Transerver 负责服务器身份、路由、本地 outbox/inbox、重试、最终回执、去重、认证和运行指标。Distant Stock 负责包裹编码、库存、地址、订单、互通塔及世界线程操作。

公共 API 不写死 Distant Stock、HTTP、文件目录或单 Router。首版实现通过下面几项小型接口装配：

- `Transport`：发送消息、拉取消息和交换回执；
- `MessageStore`：保存 outbox、inbox、回执与完成记录；
- `Authenticator`：对传输请求签名和验证；
- `RouteResolver`：将目标 `serverId` 解析为下一跳；
- `MessageHandler`：上层频道处理器。

默认实现分别是 HTTP、本地原子文件、共享密钥 HMAC 和单 Router 路由。未来实现可以替换其中一项，不需要修改消息格式或业务模组。

## 2. 多节点网络

```text
Server A ─┐
Server B ─┤
Server C ─┼── Router
Server D ─┤
Server E ─┘
```

Router 是首个 `RouteResolver + Transport` 实现，可以嵌入任意 Minecraft 服务器。它不是仓储服，不保存上层业务状态。每个节点有稳定且唯一的 `serverId`；协议没有 host/client 角色。未来可以增加直连、双 Router 或其它发现方式。

首版支持单播以及由多个单播组成的多播。广播仅用于轻量节点目录和能力信息，不用于复制物品。

## 3. 消息

每条消息包含：

- `messageId`：全局唯一，所有重试保持不变；
- `channel`：例如 `distantstock:package`；
- `source` 与 `destination`：明确的服务器 ID；
- `correlationId`：可选，用于关联订单及其包裹；
- `createdAt` 与 `expiresAt`；
- `contentType`；
- `payload`：最多 2 MiB 的二进制载荷；
- `SHA-256`：编码时写入并在解码时验证。

未知目标必须失败，不能退回任意默认节点。

## 4. 投递状态

```text
origin outbox
    │
    ▼
router relay store ─── RELAYED
    │
    ▼
destination inbox ─── RECEIVED
    │
    ▼
application handler ─ APPLIED / REJECTED
    │
    ▼
origin receives final receipt and clears outbox
```

Router 返回 `RELAYED` 只代表已经安全接管消息。发送端只有收到目标节点的 `APPLIED` 或 `REJECTED` 最终回执后才清理 outbox。

网络层采用至少一次投递。上层使用 `messageId` 实现幂等处理，从而得到实际的一次性物品效果。

## 5. 本地存储

默认的文件存储实现使用以下目录：

```text
transerver/
  outbox/
  inbox/
  outgoing-receipts/
  completed/
  dead-letter/
```

Router 使用：

```text
transerver-router/
  relay/<destination>/
  receipts/<source>/
```

文件通过同目录临时文件、强制落盘和原子改名提交。进程启动时从目录重建队列，不要求外置数据库。`MessageStore` 接口允许以后增加内嵌数据库或服务器群已有的存储适配器，但上层不能依赖其实现细节。

## 6. API

上层注册频道处理器并发送字节载荷。`send` 在本地 outbox 写入成功后返回句柄；句柄的 future 在收到最终回执时完成。

处理器返回：

- `APPLIED`：处理完成；
- `RETRY`：资源暂不可用，消息留在 inbox；
- `REJECTED`：永久错误，写入 dead-letter 并发回错误回执。

处理器必须根据 `messageId` 保持幂等。NeoForge 适配层负责把需要修改世界的工作调度到服务器主线程。

## 7. 认证

节点共享一个网络密钥。请求使用 HMAC-SHA256，覆盖方法、路径、节点 ID、时间戳、nonce 和正文摘要。Router 拒绝超时请求与当前进程内重复 nonce。

认证不替代传输加密。公网部署应使用 TLS 反向代理或受控隧道。

## 8. Distant Stock 接入

Distant Stock 使用独立频道：

```text
distantstock:order.request
distantstock:order.result
distantstock:package
distantstock:stock.snapshot
```

同一仓储频率可以存在于多个服务器。Distant Stock 根据库存和状态选择来源服务器，并可将一个订单拆成多个带相同 `correlationId` 的子订单。Create 地址只负责目标服务器内部选择远仓港。

迁移顺序：可靠包裹、订单与回程、库存目录、监视器指标。
