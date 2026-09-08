package dev.transerver.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacAuthenticatorTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsOneAuthenticRequestAndRejectsReplayOrModification() {
        var authenticator = new HmacAuthenticator(SECRET);
        byte[] body = "message".getBytes(StandardCharsets.UTF_8);
        var proof = authenticator.sign("alpha", "POST", "/v1/messages", body);

        assertTrue(authenticator.verify(proof, "POST", "/v1/messages", body));
        assertFalse(authenticator.verify(proof, "POST", "/v1/messages", body));

        var changedProof = authenticator.sign("alpha", "POST", "/v1/messages", body);
        assertFalse(authenticator.verify(changedProof, "POST", "/v1/messages",
                "changed".getBytes(StandardCharsets.UTF_8)));
    }
}
