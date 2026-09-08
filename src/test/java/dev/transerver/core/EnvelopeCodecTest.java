package dev.transerver.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvelopeCodecTest {
    private final EnvelopeCodec codec = new EnvelopeCodec();

    @Test
    void roundTripsEnvelope() throws Exception {
        var original = new MessageEnvelope(UUID.randomUUID(), "distantstock:package", "alpha", "beta",
                "order-7", Instant.ofEpochMilli(1000), Instant.ofEpochMilli(9000),
                "application/x-nbt", new byte[]{1, 2, 3, 4});

        MessageEnvelope decoded = codec.decode(codec.encode(original));

        assertEquals(original.messageId(), decoded.messageId());
        assertEquals(original.channel(), decoded.channel());
        assertEquals(original.source(), decoded.source());
        assertEquals(original.destination(), decoded.destination());
        assertEquals(original.correlationId(), decoded.correlationId());
        assertEquals(original.createdAt(), decoded.createdAt());
        assertEquals(original.expiresAt(), decoded.expiresAt());
        assertEquals(original.contentType(), decoded.contentType());
        assertArrayEquals(original.payload(), decoded.payload());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        var message = new MessageEnvelope(UUID.randomUUID(), "test:data", "alpha", "beta", null,
                Instant.now(), null, "application/octet-stream", new byte[]{7, 8, 9});
        byte[] encoded = codec.encode(message);
        encoded[encoded.length - 33] ^= 1;

        assertThrows(IOException.class, () -> codec.decode(encoded));
    }

    @Test
    void rejectsOversizePayloadBeforeAllocation() {
        var message = new MessageEnvelope(UUID.randomUUID(), "test:data", "alpha", "beta", null,
                Instant.now(), null, "application/octet-stream",
                new byte[EnvelopeCodec.MAX_PAYLOAD_BYTES + 1]);

        assertThrows(IOException.class, () -> codec.encode(message));
    }
}
