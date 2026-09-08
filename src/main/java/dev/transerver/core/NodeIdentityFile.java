package dev.transerver.core;

import dev.transerver.api.NodeIdentity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;

/** Persists the stable node UUID separately from mutable transport configuration. */
public final class NodeIdentityFile {
    private static final String SCHEMA_VERSION = "1";
    private final Path path;

    public NodeIdentityFile(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public synchronized NodeIdentity loadOrCreate(String defaultAlias) throws IOException {
        if (Files.exists(path)) {
            return load();
        }
        NodeIdentity created = new NodeIdentity(UUID.randomUUID(), defaultAlias);
        save(created);
        return created;
    }

    public synchronized NodeIdentity load() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String schema = properties.getProperty("schemaVersion", "").trim();
        if (!SCHEMA_VERSION.equals(schema)) {
            throw new IOException("Unsupported node identity schema: " + schema);
        }
        try {
            return new NodeIdentity(
                    UUID.fromString(required(properties, "nodeId")),
                    required(properties, "alias"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid node identity file: " + path, exception);
        }
    }

    public synchronized NodeIdentity rename(String alias) throws IOException {
        NodeIdentity renamed = load().withAlias(alias);
        save(renamed);
        return renamed;
    }

    private void save(NodeIdentity identity) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".node-identity-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("schemaVersion", SCHEMA_VERSION);
            properties.setProperty("nodeId", identity.nodeId().toString());
            properties.setProperty("alias", identity.alias());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, "Transerver node identity - back up this file");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing identity value: " + key);
        }
        return value.trim();
    }
}
