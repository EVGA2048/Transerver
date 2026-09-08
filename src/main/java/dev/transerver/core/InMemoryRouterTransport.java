package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic transport for tests and embedded development environments. */
public final class InMemoryRouterTransport implements Transport {
    private final Set<String> nodes = new LinkedHashSet<>();
    private final Map<String, LinkedHashMap<UUID, MessageEnvelope>> relay = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<UUID, FinalReceipt>> receipts = new LinkedHashMap<>();

    public synchronized void registerNode(String nodeId) {
        nodes.add(nodeId);
    }

    @Override
    public synchronized DeliveryState relay(MessageEnvelope message) {
        requireKnownNode(message.source());
        if (!nodes.contains(message.destination())) {
            return DeliveryState.REJECTED;
        }
        var destinationQueue = relay.computeIfAbsent(message.destination(), ignored -> new LinkedHashMap<>());
        MessageEnvelope existing = destinationQueue.putIfAbsent(message.messageId(), message);
        if (existing != null && !sameMessage(existing, message)) {
            throw new IllegalArgumentException("Message ID was reused with different content");
        }
        return DeliveryState.RELAYED;
    }

    @Override
    public synchronized List<MessageEnvelope> receive(String nodeId, int limit) {
        requireKnownNode(nodeId);
        return firstValues(relay.get(nodeId), limit);
    }

    @Override
    public synchronized void submitReceipt(FinalReceipt receipt) {
        requireKnownNode(receipt.source());
        requireKnownNode(receipt.destination());
        receipts.computeIfAbsent(receipt.source(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(receipt.messageId(), receipt);
        var destinationQueue = relay.get(receipt.destination());
        if (destinationQueue != null) {
            destinationQueue.remove(receipt.messageId());
        }
    }

    @Override
    public synchronized List<FinalReceipt> receiveReceipts(String nodeId, int limit) {
        requireKnownNode(nodeId);
        return firstValues(receipts.get(nodeId), limit);
    }

    @Override
    public synchronized void acknowledgeReceipt(String nodeId, UUID messageId) {
        requireKnownNode(nodeId);
        var queue = receipts.get(nodeId);
        if (queue != null) {
            queue.remove(messageId);
        }
    }

    private void requireKnownNode(String nodeId) {
        if (!nodes.contains(nodeId)) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
    }

    private static <T> List<T> firstValues(Map<UUID, T> values, int limit) {
        if (values == null || limit <= 0) {
            return List.of();
        }
        var result = new ArrayList<T>(Math.min(values.size(), limit));
        for (T value : values.values()) {
            if (result.size() == limit) {
                break;
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static boolean sameMessage(MessageEnvelope left, MessageEnvelope right) {
        return left.messageId().equals(right.messageId())
                && left.channel().equals(right.channel())
                && left.source().equals(right.source())
                && left.destination().equals(right.destination())
                && java.util.Objects.equals(left.correlationId(), right.correlationId())
                && left.createdAt().equals(right.createdAt())
                && java.util.Objects.equals(left.expiresAt(), right.expiresAt())
                && left.contentType().equals(right.contentType())
                && Arrays.equals(left.payload(), right.payload());
    }
}
