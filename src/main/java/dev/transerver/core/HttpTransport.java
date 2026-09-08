package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class HttpTransport implements Transport {
    static final String NODE_HEADER = "X-Transerver-Node";
    static final String TIME_HEADER = "X-Transerver-Time";
    static final String NONCE_HEADER = "X-Transerver-Nonce";
    static final String SIGNATURE_HEADER = "X-Transerver-Signature";

    private final String nodeId;
    private final RouteResolver routes;
    private final Authenticator authenticator;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();
    private final WireBatchCodec batchCodec = new WireBatchCodec();

    public HttpTransport(String nodeId, RouteResolver routes, Authenticator authenticator) {
        this(nodeId, routes, authenticator, HttpClient.newHttpClient(), Duration.ofSeconds(10));
    }

    public HttpTransport(String nodeId, RouteResolver routes, Authenticator authenticator,
                         HttpClient client, Duration requestTimeout) {
        this.nodeId = nodeId;
        this.routes = routes;
        this.authenticator = authenticator;
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public DeliveryState relay(MessageEnvelope message) {
        if (!message.source().equals(nodeId)) {
            throw new IllegalArgumentException("Cannot relay a message owned by another node");
        }
        if (routes.resolve(message.destination()).isEmpty()) {
            return DeliveryState.REJECTED;
        }
        try {
            byte[] body = envelopeCodec.encode(message);
            String state = new String(request(message.destination(), "POST", "/v1/messages", body),
                    StandardCharsets.UTF_8);
            try {
                return DeliveryState.valueOf(state);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Router returned an invalid relay state: " + state, exception);
            }
        } catch (IOException exception) {
            throw new TranserverException("Unable to encode outgoing message", exception);
        }
    }

    @Override
    public List<MessageEnvelope> receive(String requestedNodeId, int limit) {
        requireLocalNode(requestedNodeId);
        String target = "/v1/messages?node=" + encode(nodeId) + "&limit=" + boundedLimit(limit);
        try {
            return batchCodec.decodeMessages(request(nodeId, "GET", target, new byte[0]));
        } catch (IOException exception) {
            throw new TranserverException("Unable to decode incoming message batch", exception);
        }
    }

    @Override
    public void submitReceipt(FinalReceipt receipt) {
        if (!receipt.destination().equals(nodeId)) {
            throw new IllegalArgumentException("Cannot submit a receipt owned by another node");
        }
        try {
            request(receipt.source(), "POST", "/v1/receipts", receiptCodec.encode(receipt));
        } catch (IOException exception) {
            throw new TranserverException("Unable to encode final receipt", exception);
        }
    }

    @Override
    public List<FinalReceipt> receiveReceipts(String requestedNodeId, int limit) {
        requireLocalNode(requestedNodeId);
        String target = "/v1/receipts?node=" + encode(nodeId) + "&limit=" + boundedLimit(limit);
        try {
            return batchCodec.decodeReceipts(request(nodeId, "GET", target, new byte[0]));
        } catch (IOException exception) {
            throw new TranserverException("Unable to decode receipt batch", exception);
        }
    }

    @Override
    public void acknowledgeReceipt(String requestedNodeId, UUID messageId) {
        requireLocalNode(requestedNodeId);
        String target = "/v1/receipts?node=" + encode(nodeId) + "&id=" + encode(messageId.toString());
        request(nodeId, "DELETE", target, new byte[0]);
    }

    private byte[] request(String routeKey, String method, String target, byte[] body) {
        URI base = routes.resolve(routeKey)
                .orElseThrow(() -> new IllegalArgumentException("No route for node: " + routeKey));
        URI uri = base.resolve(target);
        var proof = authenticator.sign(nodeId, method, target, body);
        var builder = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                .header(NODE_HEADER, proof.nodeId())
                .header(TIME_HEADER, Long.toString(proof.timestamp()))
                .header(NONCE_HEADER, proof.nonce())
                .header(SIGNATURE_HEADER, proof.signature());
        if (method.equals("POST")) {
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
        } else if (method.equals("DELETE")) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                String detail = new String(response.body(), StandardCharsets.UTF_8);
                throw new TranserverException("Router returned HTTP " + response.statusCode() + ": " + detail,
                        new IOException("Router rejected request"));
            }
            return response.body();
        } catch (IOException exception) {
            throw new TranserverException("HTTP transport failed for " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranserverException("HTTP transport was interrupted", exception);
        }
    }

    private void requireLocalNode(String requestedNodeId) {
        if (!nodeId.equals(requestedNodeId)) {
            throw new IllegalArgumentException("Transport belongs to " + nodeId + ", not " + requestedNodeId);
        }
    }

    private static int boundedLimit(int limit) {
        return Math.max(0, Math.min(128, limit));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
