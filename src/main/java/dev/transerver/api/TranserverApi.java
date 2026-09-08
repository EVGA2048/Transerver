package dev.transerver.api;

public interface TranserverApi {
    void registerHandler(String channel, MessageHandler handler);

    SendHandle send(String destination, String channel, byte[] payload, SendOptions options);
}
