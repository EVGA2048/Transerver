package dev.transerver.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranserverServicesTest {
    private TranserverApi installed;
    private final NodeIdentity identity = new NodeIdentity(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "test");

    @AfterEach
    void clearService() {
        if (installed != null) {
            TranserverServices.clear(installed);
        }
    }

    @Test
    void exposesOneRuntimeAndClearsOnlyItsOwner() {
        installed = new StubApi();

        TranserverServices.install(installed, identity);

        assertEquals(installed, TranserverServices.api().orElseThrow());
        assertEquals(identity, TranserverServices.identity().orElseThrow());
        TranserverServices.clear(new StubApi());
        assertEquals(installed, TranserverServices.api().orElseThrow());
        TranserverServices.clear(installed);
        assertTrue(TranserverServices.api().isEmpty());
        assertTrue(TranserverServices.identity().isEmpty());
        installed = null;
    }

    @Test
    void refusesToReplaceRunningService() {
        installed = new StubApi();
        TranserverServices.install(installed, identity);

        assertThrows(IllegalStateException.class, () -> TranserverServices.install(new StubApi(), identity));
    }

    private static final class StubApi implements TranserverApi {
        @Override
        public void registerHandler(String channel, MessageHandler handler) {
        }

        @Override
        public SendHandle send(String destination, String channel, byte[] payload, SendOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NodeStatus status() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CompletedSend> completedSends(int limit) {
            return List.of();
        }

        @Override
        public void acknowledgeCompletedSend(UUID messageId) {
        }
    }
}
