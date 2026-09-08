package dev.transerver.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpRouterServer implements AutoCloseable {
    private static final int MAX_MESSAGE_REQUEST_BYTES = EnvelopeCodec.MAX_PAYLOAD_BYTES + 16 * 1024;
    private static final int MAX_RECEIPT_REQUEST_BYTES = 16 * 1024;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Transport router;
    private final Authenticator authenticator;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();
    private final WireBatchCodec batchCodec = new WireBatchCodec();

    public HttpRouterServer(InetSocketAddress address, Transport router, Authenticator authenticator)
            throws IOException {
        this.router = router;
        this.authenticator = authenticator;
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/hello", this::handleHello);
        server.createContext("/v1/messages", this::handleMessages);
        server.createContext("/v1/receipts", this::handleReceipts);
    }

    public void start() {
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    public URI baseUri() {
        String host = address().getAddress().getHostAddress();
        return URI.create("http://" + host + ":" + address().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void handleHello(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange, 405, bytes("Method not allowed"));
            return;
        }
        respond(exchange, 200, bytes("transerver/1"));
    }

    private void handleMessages(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (method.equals("POST")) {
                byte[] body = readLimited(exchange, MAX_MESSAGE_REQUEST_BYTES);
                var proof = authenticate(exchange, body);
                MessageEnvelope message = envelopeCodec.decode(body);
                requireOwner(proof.nodeId(), message.source());
                var state = router.relay(message);
                respond(exchange, 202, bytes(state.name()));
            } else if (method.equals("GET")) {
                var proof = authenticate(exchange, new byte[0]);
                Map<String, String> query = query(exchange);
                String nodeId = required(query, "node");
                requireOwner(proof.nodeId(), nodeId);
                int limit = limit(query);
                respond(exchange, 200, batchCodec.encodeMessages(router.receive(nodeId, limit)));
            } else {
                respond(exchange, 405, bytes("Method not allowed"));
            }
        } catch (SecurityException exception) {
            respond(exchange, 401, bytes(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, bytes(exception.getMessage()));
        } catch (Exception exception) {
            respond(exchange, 500, bytes("Router failure"));
        }
    }

    private void handleReceipts(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (method.equals("POST")) {
                byte[] body = readLimited(exchange, MAX_RECEIPT_REQUEST_BYTES);
                var proof = authenticate(exchange, body);
                FinalReceipt receipt = receiptCodec.decode(body);
                requireOwner(proof.nodeId(), receipt.destination());
                router.submitReceipt(receipt);
                respond(exchange, 202, new byte[0]);
            } else if (method.equals("GET")) {
                var proof = authenticate(exchange, new byte[0]);
                Map<String, String> query = query(exchange);
                String nodeId = required(query, "node");
                requireOwner(proof.nodeId(), nodeId);
                respond(exchange, 200,
                        batchCodec.encodeReceipts(router.receiveReceipts(nodeId, limit(query))));
            } else if (method.equals("DELETE")) {
                var proof = authenticate(exchange, new byte[0]);
                Map<String, String> query = query(exchange);
                String nodeId = required(query, "node");
                requireOwner(proof.nodeId(), nodeId);
                router.acknowledgeReceipt(nodeId, UUID.fromString(required(query, "id")));
                respond(exchange, 204, new byte[0]);
            } else {
                respond(exchange, 405, bytes("Method not allowed"));
            }
        } catch (SecurityException exception) {
            respond(exchange, 401, bytes(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, bytes(exception.getMessage()));
        } catch (Exception exception) {
            respond(exchange, 500, bytes("Router failure"));
        }
    }

    private Authenticator.AuthProof authenticate(HttpExchange exchange, byte[] body) {
        String node = firstHeader(exchange, HttpTransport.NODE_HEADER);
        String timestamp = firstHeader(exchange, HttpTransport.TIME_HEADER);
        String nonce = firstHeader(exchange, HttpTransport.NONCE_HEADER);
        String signature = firstHeader(exchange, HttpTransport.SIGNATURE_HEADER);
        long parsedTimestamp;
        try {
            parsedTimestamp = Long.parseLong(timestamp);
        } catch (RuntimeException exception) {
            throw new SecurityException("Invalid authentication timestamp");
        }
        var proof = new Authenticator.AuthProof(node, parsedTimestamp, nonce, signature);
        String target = rawTarget(exchange);
        if (!authenticator.verify(proof, exchange.getRequestMethod(), target, body)) {
            throw new SecurityException("Authentication failed");
        }
        return proof;
    }

    private static String firstHeader(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value == null || value.isBlank()) {
            throw new SecurityException("Missing authentication header");
        }
        return value;
    }

    private static byte[] readLimited(HttpExchange exchange, int maximum) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(maximum + 1);
        if (body.length > maximum) {
            throw new IllegalArgumentException("Request body is too large");
        }
        return body;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        var values = new LinkedHashMap<String, String>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate query parameter: " + key);
            }
        }
        return values;
    }

    private static int limit(Map<String, String> query) {
        int limit = Integer.parseInt(query.getOrDefault("limit", "128"));
        if (limit < 0 || limit > 128) {
            throw new IllegalArgumentException("limit must be between 0 and 128");
        }
        return limit;
    }

    private static String required(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing query parameter: " + key);
        }
        return value;
    }

    private static void requireOwner(String authenticatedNode, String owner) {
        if (!authenticatedNode.equals(owner)) {
            throw new SecurityException("Authenticated node does not own this operation");
        }
    }

    private static String rawTarget(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        return exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
