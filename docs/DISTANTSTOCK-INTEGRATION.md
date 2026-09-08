# Distant Stock integration contract

## Stable identity, mutable address

A physical address is never package data. IP addresses, domains, ports, Router URLs and transport names belong only to configuration and may change at any time.

Every server instance owns an immutable `nodeId`. A remote package stores the destination `nodeId`; `RouteResolver` resolves that logical ID to the current next hop only when the remote dock sends it. Changing ZeroTier addresses, DNS names, ports, HTTP/WebSocket adapters or Router topology therefore does not invalidate an existing package.

The recommended identity has two representations:

- storage and protocol: a random 128-bit UUID;
- UI: a server alias plus a short grouped fingerprint, for example `生存服 · 1A2B3C4D-5E6F7G8H`.

The fingerprint is for recognition, not authentication. The full UUID remains authoritative. A node identity is generated once, displayed read-only in the configuration UI and included in backups. Renaming the alias is safe; changing the identity is an explicit migration operation. Cloning a whole server requires generating a new identity to avoid a collision.

The first Router topology is even less dependent on server addresses: every node makes outbound requests to one Router, so the Router does not need a reachable IP for each Minecraft server. A future direct or multi-Router transport can provide another `RouteResolver` without changing package components.

## Package route component

Distant Stock registers a custom package data component with these logical fields:

```text
schemaVersion
destinationNodeId
correlationId
childOrderId
```

Create's address remains unchanged and is used only after the package reaches the destination Minecraft server. The logistics frequency identifies a warehouse network and must not be reused as server identity because the same frequency may be present on several nodes.

When a remote order is accepted, Distant Stock records the originating node against Create's `PackagingRequest.orderId`. `RemotePackagerBlockEntity` copies the matching route component onto every produced remote package. Its existing `transmuteCopy` preserves all Create and Distant Stock data components.

The current address-keyed `ReturnRoute` is removed after migration. Two players using the same Create address must not affect each other's cross-server route.

## Unknown and changed routes

An unknown `nodeId` never falls back to the first configured server. The dock reports an unresolved destination and keeps the physical package or restores it from the durable send result. It must not silently destroy the item or send it elsewhere.

Transerver persists both the outgoing payload and its final `APPLIED` or `REJECTED` result. Distant Stock acknowledges that result only after its main-thread follow-up succeeds. Consequently, a source-server restart cannot lose the action that returns a rejected package.

Recommended UI and tooltip behavior:

- known node: show alias and short fingerprint;
- route temporarily offline: show the same identity with an offline indicator and retain for retry;
- identity absent from configuration: mark `未知目的服务器`, do not extract into an unobservable queue;
- alias or address changed: old packages automatically display the current alias and use the current route.

## Channels

Transerver treats these names as opaque; Distant Stock owns their schemas.

```text
distantstock:v1/network.announce
distantstock:v1/stock.query
distantstock:v1/stock.result
distantstock:v1/order.request
distantstock:v1/order.result
distantstock:v1/package.dispatch
```

All payloads carry an explicit schema version. Requests and their replies share a `correlationId`. A multi-server order is split into child orders, each addressed to one source node; package dispatches inherit that child order ID.

## Application acknowledgement

`package.dispatch` is not `APPLIED` merely because bytes reached the destination. Its handler schedules work on the Minecraft server thread and returns:

- `APPLIED` only after the package was inserted into the selected remote dock;
- `RETRY` while the dock is full, unloaded or temporarily unavailable;
- `REJECTED` for invalid NBT, an invalid route component or a permanent policy error.

This boundary, combined with Transerver message-ID deduplication, prevents a retry from materializing the same package twice.

## Configuration UI

The initial UI should expose:

- local server alias;
- read-only full `nodeId` and copy button;
- Router URL or selected transport profile;
- connection test and authenticated Router identity;
- discovered node aliases, IDs and online state;
- an advanced route override table supplied by `RouteResolver` implementations.

The UI edits not store an address inside an item when a route is selected. It writes only the destination identity component.
