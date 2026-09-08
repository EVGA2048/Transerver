package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FinalReceipt(
        UUID messageId,
        String source,
        String destination,
        DeliveryState state,
        String detail,
        Instant completedAt
) {
    public FinalReceipt {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completedAt, "completedAt");
        if (state != DeliveryState.APPLIED && state != DeliveryState.REJECTED) {
            throw new IllegalArgumentException("Final receipt must be APPLIED or REJECTED");
        }
    }
}
