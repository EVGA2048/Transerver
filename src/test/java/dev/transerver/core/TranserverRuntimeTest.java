package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.DeliveryState;
import dev.transerver.api.SendOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranserverRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scheduledRuntimesDeliverAndExposeStatus() throws Exception {
        var router = new InMemoryRouterTransport();
        router.registerNode("alpha");
        router.registerNode("beta");
        var alpha = node("alpha", router);
        var beta = node("beta", router);
        beta.registerHandler("test:parcel",
                message -> CompletableFuture.completedFuture(DeliveryResult.APPLIED));
        var send = alpha.send("beta", "test:parcel",
                "parcel".getBytes(StandardCharsets.UTF_8), SendOptions.defaults());

        try (var alphaRuntime = new TranserverRuntime(alpha, Duration.ofMillis(10));
             var betaRuntime = new TranserverRuntime(beta, Duration.ofMillis(10))) {
            alphaRuntime.start();
            betaRuntime.start();

            var receipt = send.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(DeliveryState.APPLIED, receipt.state());
            assertTrue(alpha.status().transportUp());
            assertNotNull(alpha.status().lastSuccessfulPump());
            assertEquals(0, alpha.status().outboxDepth());
        }
    }

    private TranserverNode node(String nodeId, Transport transport) throws Exception {
        return new TranserverNode(nodeId, transport,
                new FileMessageStore(temporaryDirectory.resolve(nodeId)));
    }
}
