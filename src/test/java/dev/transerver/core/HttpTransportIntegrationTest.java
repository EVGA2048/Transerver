package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.DeliveryState;
import dev.transerver.api.SendOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpTransportIntegrationTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void threeNodesExchangeDurableMessagesOverHttp() throws Exception {
        var durableRouter = new FileRouterTransport(
                new FileMessageStore(temporaryDirectory.resolve("router")),
                Set.of("alpha", "beta", "gamma"));
        try (var server = new HttpRouterServer(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                durableRouter, new HmacAuthenticator(SECRET))) {
            server.start();
            RouteResolver routes = ignored -> java.util.Optional.of(server.baseUri());
            var alpha = node("alpha", routes);
            var beta = node("beta", routes);
            var gamma = node("gamma", routes);
            List<String> received = new ArrayList<>();
            beta.registerHandler("test:parcel", message -> {
                received.add("beta:" + text(message.payload()));
                return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
            });
            gamma.registerHandler("test:parcel", message -> {
                received.add("gamma:" + text(message.payload()));
                return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
            });

            var toBeta = alpha.send("beta", "test:parcel", bytes("one"), SendOptions.defaults());
            var toGamma = alpha.send("gamma", "test:parcel", bytes("two"), SendOptions.defaults());
            alpha.pump();
            beta.pump();
            gamma.pump();
            alpha.pump();

            assertEquals(List.of("beta:one", "gamma:two"), received);
            assertEquals(DeliveryState.APPLIED, toBeta.completion().toCompletableFuture().join().state());
            assertEquals(DeliveryState.APPLIED, toGamma.completion().toCompletableFuture().join().state());
            assertEquals(0, alpha.pendingOutboxCount());
        }
    }

    @Test
    void lateReceiptAfterApplicationAcknowledgementDoesNotDisconnectSender() throws Exception {
        var router = new FileRouterTransport(new FileMessageStore(temporaryDirectory.resolve("router")),
                Set.of("alpha", "beta"));
        try (var server = new HttpRouterServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                router, new HmacAuthenticator(SECRET))) {
            server.start();
            RouteResolver routes = ignored -> java.util.Optional.of(server.baseUri());
            var alpha = node("alpha", routes);
            var beta = node("beta", routes);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            beta.registerHandler("test:parcel", message -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
            });
            var sent = alpha.send("beta", "test:parcel", bytes("one parcel"), SendOptions.defaults());
            alpha.pump();
            beta.pump();
            // Sender retries its outbox before receiving the first final receipt.
            alpha.pump();
            alpha.acknowledgeCompletedSend(sent.messageId());
            // The destination deduplicates the retry but replays its durable receipt.
            beta.pump();
            var restarted = node("alpha", routes);
            restarted.pump();
            assertEquals(1, calls.get());
            assertEquals(0, restarted.pendingOutboxCount());
            assertEquals(0, restarted.status().completedSendDepth());
            org.junit.jupiter.api.Assertions.assertTrue(restarted.status().transportUp());
            assertEquals(0, router.receiveReceipts("alpha", 10).size());
        }
    }

    private TranserverNode node(String nodeId, RouteResolver routes) throws Exception {
        var transport = new HttpTransport(nodeId, routes, new HmacAuthenticator(SECRET));
        return new TranserverNode(nodeId, transport,
                new FileMessageStore(temporaryDirectory.resolve(nodeId)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
