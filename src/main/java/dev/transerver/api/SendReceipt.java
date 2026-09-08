package dev.transerver.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SendReceipt(UUID messageId, DeliveryState state, String detail, Instant completedAt) {
    public SendReceipt {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
