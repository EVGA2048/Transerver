package dev.transerver.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record CompletedSend(
        UUID messageId,
        String channel,
        String destination,
        String correlationId,
        byte[] payload,
        DeliveryState state,
        String detail,
        Instant completedAt
) {
    public CompletedSend {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(destination, "destination");
        payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completedAt, "completedAt");
        detail = detail == null ? "" : detail;
        if (state != DeliveryState.APPLIED && state != DeliveryState.REJECTED) {
            throw new IllegalArgumentException("Completed send must be APPLIED or REJECTED");
        }
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
