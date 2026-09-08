package dev.transerver.core;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface MessageStore {
    void put(String area, String key, byte[] value) throws IOException;

    Optional<byte[]> get(String area, String key) throws IOException;

    List<StoredEntry> list(String area, int limit) throws IOException;

    void remove(String area, String key) throws IOException;

    record StoredEntry(String key, byte[] value) {
    }
}
