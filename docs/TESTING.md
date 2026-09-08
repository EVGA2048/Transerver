# Manual multi-server test

This probe tests Transerver itself before Minecraft or Distant Stock is involved.

## 1. Start the Router

Copy `transerver-router.example.properties` to `transerver-router.properties`. Set at least three unique node IDs and replace `networkSecret`. Build and start:

```bash
./gradlew installDist
./build/install/Transerver/bin/Transerver transerver-router.properties
```

## 2. Start three probe nodes

On each machine, copy `transerver-probe.example.properties`, give it one of the Router node IDs, set the Router URL, and use exactly the same secret. Each node needs its own data directory.

```bash
./build/install/Transerver/bin/Transerver probe transerver-probe.properties
```

At the prompt, send a message to another node:

```text
send creative hello from survival
```

The sender first prints a stable message ID. The destination prints the message, then the sender prints `APPLIED` after the final receipt returns.

## 3. Failure tests

1. Stop a destination node, send several messages to it, and check `status`: the sender outbox stays non-zero.
2. Restart the destination with the same node ID and data directory: it receives the pending messages and the sender eventually reports `APPLIED`.
3. Stop and restart the Router between sending and receiving: relay files remain in the Router data directory.
4. Send to an ID absent from the Router configuration: the sender reports `REJECTED`; no other node receives it.
5. Start a probe with a different secret: authentication fails and its outbox remains for retry instead of being discarded.

Do not reuse one node data directory for two running processes. For public-network tests, place TLS in front of the Router.
