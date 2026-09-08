package dev.transerver.api;

import java.time.Instant;

public record SendOptions(String correlationId, String contentType, Instant expiresAt) {
    public SendOptions {
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    public static SendOptions defaults() {
        return new SendOptions(null, "application/octet-stream", null);
    }
}
