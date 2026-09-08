package dev.transerver.core;

import dev.transerver.api.DeliveryState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRouterTransportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void messageAndReceiptSurviveRouterRestart() throws Exception {
        UUID id = UUID.randomUUID();
        var message = new MessageEnvelope(id, "test:package", "alpha", "beta", null,
                Instant.now(), null, "application/octet-stream", new byte[]{1, 2, 3});
        var first = router();

        assertEquals(DeliveryState.RELAYED, first.relay(message));
        assertEquals(id, router().receive("beta", 10).getFirst().messageId());

        var receipt = new FinalReceipt(id, "alpha", "beta", DeliveryState.APPLIED, "", Instant.now());
        router().submitReceipt(receipt);

        var restarted = router();
        assertTrue(restarted.receive("beta", 10).isEmpty());
        assertEquals(id, restarted.receiveReceipts("alpha", 10).getFirst().messageId());
        restarted.acknowledgeReceipt("alpha", id);
        assertTrue(router().receiveReceipts("alpha", 10).isEmpty());
    }

    private FileRouterTransport router() throws Exception {
        return new FileRouterTransport(new FileMessageStore(temporaryDirectory), Set.of("alpha", "beta"));
    }
}
