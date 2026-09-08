package dev.transerver.core;

import dev.transerver.api.NodeIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeIdentityFileTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOnceAndKeepsStableId() throws IOException {
        NodeIdentityFile file = new NodeIdentityFile(temporaryDirectory.resolve("node.properties"));

        NodeIdentity first = file.loadOrCreate("生存服");
        NodeIdentity second = file.loadOrCreate("ignored alias");

        assertEquals(first, second);
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("node.properties")));
    }

    @Test
    void renameDoesNotChangeIdOrFingerprint() throws IOException {
        NodeIdentityFile file = new NodeIdentityFile(temporaryDirectory.resolve("node.properties"));
        NodeIdentity before = file.loadOrCreate("生存服");

        NodeIdentity after = file.rename("创造服");

        assertEquals(before.nodeId(), after.nodeId());
        assertEquals(before.fingerprint(), after.fingerprint());
        assertNotEquals(before.alias(), after.alias());
        assertEquals(after, file.load());
    }

    @Test
    void fingerprintIsCompactAndDeterministic() {
        NodeIdentity identity = new NodeIdentity(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "test");

        assertEquals("28Z4ASZ8-KC9D792P", identity.fingerprint());
    }

    @Test
    void invalidAliasIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new NodeIdentity(UUID.randomUUID(), "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new NodeIdentity(UUID.randomUUID(), "bad\nname"));
    }
}
