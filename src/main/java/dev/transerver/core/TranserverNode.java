package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.DeliveryState;
import dev.transerver.api.CompletedSend;
import dev.transerver.api.MessageHandler;
import dev.transerver.api.NodeStatus;
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
    private static final String SEND_RESULTS = "send-results";
    // Independent of application acknowledgements: delayed receipts must remain idempotent after restart.
    private static final String SENT_RECEIPTS = "sent-receipts";
    /** Receipts that cannot safely be attached to current sender state; kept for diagnosis, not retried forever. */
    private static final String RECEIPT_QUARANTINE = "receipt-quarantine";
    private static final String DEAD_LETTER = "dead-letter";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");
    private static final int BATCH_SIZE = 128;

    private final String nodeId;
    private final Transport transport;
    private final MessageStore store;
    private final Clock clock;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();
    private final CompletedSendCodec completedSendCodec = new CompletedSendCodec();
    private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<SendReceipt>> waiting = new ConcurrentHashMap<>();
    private final Set<UUID> handling = ConcurrentHashMap.newKeySet();
    private volatile boolean transportUp;
    private volatile Instant lastSuccessfulPump;
    private volatile Instant lastFailedPump;
    private volatile String lastFailure = "";

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

    @Override
    public Set<String> knownNodes() {
        return transport.knownNodes(nodeId);
    }

    @Override
    public NodeStatus status() {
        return new NodeStatus(nodeId, transportUp, lastSuccessfulPump, lastFailedPump, lastFailure,
                depth(OUTBOX), depth(INBOX), depth(OUTGOING_RECEIPTS), depth(SEND_RESULTS),
                depth(DEAD_LETTER), handling.size());
    }

    @Override
    public java.util.List<CompletedSend> completedSends(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        try {
            var results = new java.util.ArrayList<CompletedSend>();
            for (var entry : store.list(SEND_RESULTS, limit)) {
                results.add(completedSendCodec.decode(entry.value()));
            }
            return java.util.List.copyOf(results);
        } catch (IOException exception) {
            throw new TranserverException("Unable to read completed sends", exception);
        }
    }

    @Override
    public void acknowledgeCompletedSend(UUID messageId) {
        try {
            var result = store.get(SEND_RESULTS, messageId.toString());
            if (result.isPresent()) {
                var completed = completedSendCodec.decode(result.get());
                store.put(SENT_RECEIPTS, messageId.toString(), receiptCodec.encode(new FinalReceipt(
                        completed.messageId(), nodeId, completed.destination(), completed.state(),
                        completed.detail(), completed.completedAt())));
            }
            store.remove(SEND_RESULTS, messageId.toString());
        } catch (IOException exception) {
            throw new TranserverException("Unable to acknowledge completed send", exception);
        }
    }

    /** Performs one batch while allowing independent stages to progress after another stage fails. */
    public void pump() {
        RuntimeException failure = null;
        failure = runStage("relayOutbox", this::relayOutbox, failure);
        failure = runStage("receiveMessages", this::receiveMessages, failure);
        failure = runStage("processInbox", this::processInbox, failure);
        failure = runStage("flushReceipts", this::flushReceipts, failure);
        failure = runStage("receiveFinalReceipts", this::receiveFinalReceipts, failure);
        if (failure == null) {
            transportUp = true;
            lastSuccessfulPump = clock.instant();
            lastFailure = "";
            return;
        }
        transportUp = false;
        lastFailedPump = clock.instant();
        lastFailure = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        throw failure;
    }

    public boolean pumpSafely() {
        try {
            pump();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public int pendingOutboxCount() {
        return depth(OUTBOX);
    }

    private void relayOutbox() throws IOException {
        for (var entry : store.list(OUTBOX, BATCH_SIZE)) {
            MessageEnvelope message = envelopeCodec.decode(entry.value());
            DeliveryState state = transport.relay(message);
            if (state == DeliveryState.REJECTED) {
                var receipt = new FinalReceipt(message.messageId(), message.source(), message.destination(),
                        DeliveryState.REJECTED, "Unknown or rejected destination: " + message.destination(),
                        clock.instant());
                persistCompletedSend(message, receipt);
                store.remove(OUTBOX, entry.key());
                completeHandle(receipt);
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
                quarantineReceipt(receipt, "wrong-source");
                transport.acknowledgeReceipt(nodeId, receipt.messageId());
                continue;
            }
            String key = receipt.messageId().toString();
            var outgoing = store.get(OUTBOX, key);
            if (outgoing.isPresent()) {
                MessageEnvelope message = envelopeCodec.decode(outgoing.get());
                persistCompletedSend(message, receipt);
                store.remove(OUTBOX, key);
                completeHandle(receipt);
            } else {
                var known = store.get(SENT_RECEIPTS, key);
                if (known.isEmpty()) {
                    // Upgrade old stores while their application result is still available.
                    var result = store.get(SEND_RESULTS, key);
                    if (result.isEmpty()) {
                        // Pre-sent-receipts versions deleted SEND_RESULTS as soon as the application
                        // acknowledged it. A receipt can legitimately be replayed by the durable
                        // router after that point, especially across restart/upgrade. Keep a copy
                        // for diagnosis, ACK the poison item, and let the node keep pumping.
                        quarantineReceipt(receipt, "orphan");
                        transport.acknowledgeReceipt(nodeId, receipt.messageId());
                        continue;
                    }
                    var completed = completedSendCodec.decode(result.get());
                    known = java.util.Optional.of(receiptCodec.encode(new FinalReceipt(completed.messageId(),
                            nodeId, completed.destination(), completed.state(), completed.detail(), completed.completedAt())));
                }
                if (!java.util.Arrays.equals(known.get(), receiptCodec.encode(receipt))) {
                    // Never let a conflicting replay rewrite the result already accepted by the
                    // application, but do not leave it at the Router forever either.
                    quarantineReceipt(receipt, "conflict");
                    transport.acknowledgeReceipt(nodeId, receipt.messageId());
                    continue;
                }
                store.put(SENT_RECEIPTS, key, known.get());
            }
            transport.acknowledgeReceipt(nodeId, receipt.messageId());
        }
    }

    private void quarantineReceipt(FinalReceipt receipt, String reason) throws IOException {
        String key = receipt.messageId() + "-" + reason + "-" + UUID.randomUUID();
        store.put(RECEIPT_QUARANTINE, key, receiptCodec.encode(receipt));
    }

    private void persistCompletedSend(MessageEnvelope message, FinalReceipt receipt) throws IOException {
        if (!message.messageId().equals(receipt.messageId()) || !message.source().equals(receipt.source())
                || !message.destination().equals(receipt.destination())) {
            throw new IOException("Final receipt does not match outgoing message");
        }
        store.put(SEND_RESULTS, message.messageId().toString(), completedSendCodec.encode(message, receipt));
        store.put(SENT_RECEIPTS, message.messageId().toString(), receiptCodec.encode(receipt));
    }

    private void completeHandle(FinalReceipt receipt) {
        CompletableFuture<SendReceipt> completion = waiting.remove(receipt.messageId());
        if (completion != null) {
            completion.complete(new SendReceipt(receipt.messageId(), receipt.state(),
                    receipt.detail(), receipt.completedAt()));
        }
    }

    private int depth(String area) {
        try {
            return store.list(area, Integer.MAX_VALUE).size();
        } catch (IOException exception) {
            throw new TranserverException("Unable to inspect " + area, exception);
        }
    }

    private RuntimeException runStage(String name, PumpStage stage, RuntimeException previous) {
        try {
            stage.run();
            return previous;
        } catch (IOException | RuntimeException exception) {
            Throwable cause = exception;
            StringBuilder detail = new StringBuilder();
            detail.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            for (int depth = 0; cause.getCause() != null && cause.getCause() != cause && depth < 8; depth++) {
                cause = cause.getCause();
                detail.append(" <- ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            }
            var failure = new TranserverException("Transerver pump failed [" + name + "]: "
                    + detail, exception);
            if (previous == null) return failure;
            previous.addSuppressed(failure);
            return previous;
        }
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    @FunctionalInterface
    private interface PumpStage {
        void run() throws IOException;
    }
}
