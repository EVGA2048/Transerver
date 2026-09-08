package dev.transerver.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileMessageStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndReloadsEntries() throws Exception {
        var first = new FileMessageStore(temporaryDirectory);
        first.put("outbox", "message-1", new byte[]{1, 2, 3});

        var reopened = new FileMessageStore(temporaryDirectory);

        assertArrayEquals(new byte[]{1, 2, 3}, reopened.get("outbox", "message-1").orElseThrow());
        assertEquals(1, reopened.list("outbox", 10).size());
    }

    @Test
    void refusesSameKeyWithDifferentContent() throws Exception {
        var store = new FileMessageStore(temporaryDirectory);
        store.put("inbox", "message-1", new byte[]{1});

        assertThrows(IOException.class, () -> store.put("inbox", "message-1", new byte[]{2}));
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        var store = new FileMessageStore(temporaryDirectory);

        assertThrows(IllegalArgumentException.class,
                () -> store.put("../elsewhere", "message", new byte[]{1}));
    }
}
