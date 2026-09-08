package dev.transerver.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface MessageHandler {
    CompletionStage<DeliveryResult> handle(ReceivedMessage message);
}
