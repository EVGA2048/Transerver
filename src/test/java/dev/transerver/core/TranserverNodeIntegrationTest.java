package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.DeliveryState;
import dev.transerver.api.SendOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranserverNodeIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void routesIndependentlyBetweenThreeServers() throws Exception {
        var router = router("alpha", "beta", "gamma");
        var alpha = node("alpha", router);
        var beta = node("beta", router);
        var gamma = node("gamma", router);
        List<String> received = new ArrayList<>();
        beta.registerHandler("test:package", message -> {
            received.add("beta:" + text(message.payload()));
            return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
        });
        gamma.registerHandler("test:package", message -> {
            received.add("gamma:" + text(message.payload()));
            return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
        });

        var betaSend = alpha.send("beta", "test:package", bytes("one"), SendOptions.defaults());
        var gammaSend = alpha.send("gamma", "test:package", bytes("two"), SendOptions.defaults());
        alpha.pump();

        assertEquals(2, alpha.pendingOutboxCount());
        assertFalse(betaSend.completion().toCompletableFuture().isDone());

        beta.pump();
        gamma.pump();
        alpha.pump();

        assertEquals(List.of("beta:one", "gamma:two"), received);
        assertEquals(0, alpha.pendingOutboxCount());
        assertEquals(DeliveryState.APPLIED, betaSend.completion().toCompletableFuture().join().state());
        assertEquals(DeliveryState.APPLIED, gammaSend.completion().toCompletableFuture().join().state());
    }

    @Test
    void destinationCanRecoverAfterRestartWithoutApplyingTwice() throws Exception {
        var router = router("alpha", "beta");
        var alpha = node("alpha", router);
        var betaStore = new FileMessageStore(temporaryDirectory.resolve("beta"));
        var beta = new TranserverNode("beta", router, betaStore);
        var handlerResult = new CompletableFuture<DeliveryResult>();
        var calls = new AtomicInteger();
        beta.registerHandler("test:package", message -> {
            calls.incrementAndGet();
            return handlerResult;
        });
        var send = alpha.send("beta", "test:package", bytes("parcel"), SendOptions.defaults());

        alpha.pump();
        beta.pump();
        handlerResult.complete(DeliveryResult.APPLIED);

        var restarted = new TranserverNode("beta", router,
                new FileMessageStore(temporaryDirectory.resolve("beta")));
        restarted.registerHandler("test:package", message -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
        });
        restarted.pump();
        alpha.pump();

        assertEquals(1, calls.get());
        assertEquals(DeliveryState.APPLIED, send.completion().toCompletableFuture().join().state());
    }

    @Test
    void unknownDestinationNeverFallsBackToAnotherServer() throws Exception {
        var router = router("alpha", "beta");
        var alpha = node("alpha", router);
        alpha.send("missing", "test:package", bytes("parcel"), SendOptions.defaults());

        assertThrows(IllegalArgumentException.class, alpha::pump);
        assertEquals(1, alpha.pendingOutboxCount());
        assertTrue(router.receive("beta", 10).isEmpty());
    }

    private InMemoryRouterTransport router(String... nodeIds) {
        var router = new InMemoryRouterTransport();
        for (String nodeId : nodeIds) {
            router.registerNode(nodeId);
        }
        return router;
    }

    private TranserverNode node(String nodeId, Transport transport) throws Exception {
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
