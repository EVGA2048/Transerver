package dev.transerver.api;

import java.util.List;
import java.util.UUID;

public interface TranserverApi {
    void registerHandler(String channel, MessageHandler handler);

    SendHandle send(String destination, String channel, byte[] payload, SendOptions options);

    NodeStatus status();

    List<CompletedSend> completedSends(int limit);

    void acknowledgeCompletedSend(UUID messageId);
}
