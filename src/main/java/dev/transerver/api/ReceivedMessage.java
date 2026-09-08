package dev.transerver.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReceivedMessage(
        UUID messageId,
        String channel,
        String source,
        String destination,
        String correlationId,
        Instant createdAt,
        Instant expiresAt,
        String contentType,
        byte[] payload
) {
    public ReceivedMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(contentType, "contentType");
        payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public Optional<String> optionalCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    public Optional<Instant> optionalExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }
}
