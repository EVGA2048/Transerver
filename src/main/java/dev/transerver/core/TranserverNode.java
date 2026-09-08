package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.DeliveryState;
import dev.transerver.api.MessageHandler;
import dev.transerver.api.ReceivedMessage;
import dev.transerver.api.SendHandle;
import dev.transerver.api.SendOptions;
import dev.transerver.api.SendReceipt;
import dev.transerver.api.TranserverApi;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class TranserverNode implements TranserverApi {
    private static final String OUTBOX = "outbox";
    private static final String INBOX = "inbox";
    private static final String OUTGOING_RECEIPTS = "outgoing-receipts";
    private static final String COMPLETED = "completed";
    private static final String DEAD_LETTER = "dead-letter";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");
    private static final int BATCH_SIZE = 128;

    private final String nodeId;
    private final Transport transport;
    private final MessageStore store;
    private final Clock clock;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();
    private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<SendReceipt>> waiting = new ConcurrentHashMap<>();
    private final Set<UUID> handling = ConcurrentHashMap.newKeySet();

    public TranserverNode(String nodeId, Transport transport, MessageStore store) {
        this(nodeId, transport, store, Clock.systemUTC());
    }

    public TranserverNode(String nodeId, Transport transport, MessageStore store, Clock clock) {
        requireIdentifier(nodeId, "nodeId");
        this.nodeId = nodeId;
        this.transport = transport;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void registerHandler(String channel, MessageHandler handler) {
        requireIdentifier(channel, "channel");
        if (handlers.putIfAbsent(channel, handler) != null) {
            throw new IllegalStateException("Handler already registered for " + channel);
        }
    }

    @Override
    public SendHandle send(String destination, String channel, byte[] payload, SendOptions options) {
        requireIdentifier(destination, "destination");
        requireIdentifier(channel, "channel");
        SendOptions actualOptions = options == null ? SendOptions.defaults() : options;
        UUID messageId = UUID.randomUUID();
        var envelope = new MessageEnvelope(messageId, channel, nodeId, destination,
                actualOptions.correlationId(), clock.instant(), actualOptions.expiresAt(),
                actualOptions.contentType(), payload);
        var completion = new CompletableFuture<SendReceipt>();
        try {
            store.put(OUTBOX, messageId.toString(), envelopeCodec.encode(envelope));
            waiting.put(messageId, completion);
            return new SendHandle(messageId, completion);
        } catch (IOException exception) {
            throw new TranserverException("Unable to persist outgoing message", exception);
        }
    }

    /** Performs one non-blocking batch of relay, receive, dispatch and receipt work. */
    public void pump() {
        try {
            relayOutbox();
            receiveMessages();
            processInbox();
            flushReceipts();
            receiveFinalReceipts();
        } catch (IOException exception) {
            throw new TranserverException("Transerver pump failed", exception);
        }
    }

    public int pendingOutboxCount() {
        try {
            return store.list(OUTBOX, Integer.MAX_VALUE).size();
        } catch (IOException exception) {
            throw new TranserverException("Unable to inspect outbox", exception);
        }
    }

    private void relayOutbox() throws IOException {
        for (var entry : store.list(OUTBOX, BATCH_SIZE)) {
            MessageEnvelope message = envelopeCodec.decode(entry.value());
            DeliveryState state = transport.relay(message);
            if (state == DeliveryState.REJECTED) {
                store.remove(OUTBOX, entry.key());
                CompletableFuture<SendReceipt> completion = waiting.remove(message.messageId());
                if (completion != null) {
                    completion.complete(new SendReceipt(message.messageId(), DeliveryState.REJECTED,
                            "Unknown or rejected destination: " + message.destination(), clock.instant()));
                }
            } else if (state != DeliveryState.RELAYED) {
                throw new IOException("Transport returned invalid relay state: " + state);
            }
        }
    }

    private void receiveMessages() throws IOException {
        for (MessageEnvelope message : transport.receive(nodeId, BATCH_SIZE)) {
            if (!message.destination().equals(nodeId)) {
                throw new IOException("Transport delivered a message for another node");
            }
            store.put(INBOX, message.messageId().toString(), envelopeCodec.encode(message));
        }
    }

    private void processInbox() throws IOException {
        for (var entry : store.list(INBOX, BATCH_SIZE)) {
            MessageEnvelope message = envelopeCodec.decode(entry.value());
            String key = message.messageId().toString();
            var completed = store.get(COMPLETED, key);
            if (completed.isPresent()) {
                store.put(OUTGOING_RECEIPTS, key, completed.get());
                store.remove(INBOX, key);
                continue;
            }
            if (!handling.add(message.messageId())) {
                continue;
            }
            if (message.expiresAt() != null && !message.expiresAt().isAfter(clock.instant())) {
                finish(message, DeliveryResult.REJECTED, "Message expired");
                continue;
            }
            MessageHandler handler = handlers.get(message.channel());
            if (handler == null) {
                finish(message, DeliveryResult.REJECTED, "No handler for channel " + message.channel());
                continue;
            }
            try {
                var received = new ReceivedMessage(message.messageId(), message.channel(), message.source(),
                        message.destination(), message.correlationId(), message.createdAt(), message.expiresAt(),
                        message.contentType(), message.payload());
                handler.handle(received).whenComplete((result, error) -> {
                    if (error != null || result == null || result == DeliveryResult.RETRY) {
                        handling.remove(message.messageId());
                        return;
                    }
                    finish(message, result, result == DeliveryResult.APPLIED ? "" : "Rejected by handler");
                });
            } catch (RuntimeException exception) {
                handling.remove(message.messageId());
            }
        }
    }

    private void finish(MessageEnvelope message, DeliveryResult result, String detail) {
        String key = message.messageId().toString();
        DeliveryState state = result == DeliveryResult.APPLIED ? DeliveryState.APPLIED : DeliveryState.REJECTED;
        var receipt = new FinalReceipt(message.messageId(), message.source(), message.destination(),
                state, detail, clock.instant());
        try {
            byte[] encodedReceipt = receiptCodec.encode(receipt);
            store.put(COMPLETED, key, encodedReceipt);
            store.put(OUTGOING_RECEIPTS, key, encodedReceipt);
            if (state == DeliveryState.REJECTED) {
                store.put(DEAD_LETTER, key, envelopeCodec.encode(message));
            }
            store.remove(INBOX, key);
        } catch (IOException exception) {
            throw new TranserverException("Unable to persist final receipt", exception);
        } finally {
            handling.remove(message.messageId());
        }
    }

    private void flushReceipts() throws IOException {
        for (var entry : store.list(OUTGOING_RECEIPTS, BATCH_SIZE)) {
            transport.submitReceipt(receiptCodec.decode(entry.value()));
            store.remove(OUTGOING_RECEIPTS, entry.key());
        }
    }

    private void receiveFinalReceipts() throws IOException {
        for (FinalReceipt receipt : transport.receiveReceipts(nodeId, BATCH_SIZE)) {
            if (!receipt.source().equals(nodeId)) {
                throw new IOException("Transport delivered a receipt for another node");
            }
            String key = receipt.messageId().toString();
            store.remove(OUTBOX, key);
            CompletableFuture<SendReceipt> completion = waiting.remove(receipt.messageId());
            if (completion != null) {
                completion.complete(new SendReceipt(receipt.messageId(), receipt.state(),
                        receipt.detail(), receipt.completedAt()));
            }
            transport.acknowledgeReceipt(nodeId, receipt.messageId());
        }
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }
}
