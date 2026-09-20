package dev.transerver.api;

import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface TranserverApi {
    void registerHandler(String channel, MessageHandler handler);

    SendHandle send(String destination, String channel, byte[] payload, SendOptions options);

    NodeStatus status();

    /** Stable logical node IDs known by the current transport; never physical addresses. */
    default Set<String> knownNodes() {
        return Set.of();
    }

    List<CompletedSend> completedSends(int limit);

    void acknowledgeCompletedSend(UUID messageId);
}
