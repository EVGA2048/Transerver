package dev.transerver.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public record SendHandle(UUID messageId, CompletionStage<SendReceipt> completion) {
}
