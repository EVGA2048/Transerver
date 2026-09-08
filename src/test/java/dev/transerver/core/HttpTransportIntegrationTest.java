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
