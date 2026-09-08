package dev.transerver.api;

import java.time.Instant;
import java.util.Objects;

public record NodeStatus(
        String nodeId,
        boolean transportUp,
        Instant lastSuccessfulPump,
        Instant lastFailedPump,
        String lastFailure,
        int outboxDepth,
        int inboxDepth,
        int outgoingReceiptDepth,
        int deadLetterDepth,
        int handlersInFlight
) {
    public NodeStatus {
        Objects.requireNonNull(nodeId, "nodeId");
        lastFailure = lastFailure == null ? "" : lastFailure;
    }
}
