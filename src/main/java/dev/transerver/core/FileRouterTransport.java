package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A durable, embeddable router. Network adapters can expose this transport over any protocol. */
public final class FileRouterTransport implements Transport {
    private final MessageStore store;
    private final Set<String> nodes;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();

    public FileRouterTransport(MessageStore store, Set<String> nodes) {
        this.store = store;
        this.nodes = Set.copyOf(new LinkedHashSet<>(nodes));
    }

    @Override
    public synchronized DeliveryState relay(MessageEnvelope message) {
        requireKnownNode(message.source());
        if (!nodes.contains(message.destination())) {
            return DeliveryState.REJECTED;
        }
        try {
            store.put(relayArea(message.destination()), message.messageId().toString(),
                    envelopeCodec.encode(message));
            return DeliveryState.RELAYED;
        } catch (IOException exception) {
            throw new TranserverException("Router could not persist message", exception);
        }
    }

    @Override
    public synchronized List<MessageEnvelope> receive(String nodeId, int limit) {
        requireKnownNode(nodeId);
        try {
            var result = new ArrayList<MessageEnvelope>();
            for (var entry : store.list(relayArea(nodeId), limit)) {
                result.add(envelopeCodec.decode(entry.value()));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new TranserverException("Router could not read relay queue", exception);
        }
    }

    @Override
    public synchronized void submitReceipt(FinalReceipt receipt) {
        requireKnownNode(receipt.source());
        requireKnownNode(receipt.destination());
        String key = receipt.messageId().toString();
        try {
            var relayed = store.get(relayArea(receipt.destination()), key);
            if (relayed.isPresent()) {
                MessageEnvelope message = envelopeCodec.decode(relayed.get());
                if (!message.source().equals(receipt.source())
                        || !message.destination().equals(receipt.destination())) {
                    throw new IllegalArgumentException("Receipt route does not match relayed message");
                }
            }
            // The receipt is committed before the relay copy is removed.
            store.put(receiptArea(receipt.source()), key, receiptCodec.encode(receipt));
            store.remove(relayArea(receipt.destination()), key);
        } catch (IOException exception) {
            throw new TranserverException("Router could not persist receipt", exception);
        }
    }

    @Override
    public synchronized List<FinalReceipt> receiveReceipts(String nodeId, int limit) {
        requireKnownNode(nodeId);
        try {
            var result = new ArrayList<FinalReceipt>();
            for (var entry : store.list(receiptArea(nodeId), limit)) {
                result.add(receiptCodec.decode(entry.value()));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new TranserverException("Router could not read receipts", exception);
        }
    }

    @Override
    public synchronized void acknowledgeReceipt(String nodeId, UUID messageId) {
        requireKnownNode(nodeId);
        try {
            store.remove(receiptArea(nodeId), messageId.toString());
        } catch (IOException exception) {
            throw new TranserverException("Router could not acknowledge receipt", exception);
        }
    }

    private void requireKnownNode(String nodeId) {
        if (!nodes.contains(nodeId)) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
    }

    private static String relayArea(String nodeId) {
        return "relay-" + safeNodeName(nodeId);
    }

    private static String receiptArea(String nodeId) {
        return "receipts-" + safeNodeName(nodeId);
    }

    private static String safeNodeName(String nodeId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nodeId.getBytes(StandardCharsets.UTF_8));
    }
}
