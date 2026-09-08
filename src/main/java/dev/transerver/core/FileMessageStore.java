package dev.transerver.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FileMessageStore implements MessageStore {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private final Path root;

    public FileMessageStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public synchronized void put(String area, String key, byte[] value) throws IOException {
        Path target = entryPath(area, key);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            byte[] current = Files.readAllBytes(target);
            if (!Arrays.equals(current, value)) {
                throw new IOException("Entry already exists with different content: " + area + "/" + key);
            }
            return;
        }
        Path temporary = target.resolveSibling("." + key + "." + UUID.randomUUID() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public synchronized Optional<byte[]> get(String area, String key) throws IOException {
        Path target = entryPath(area, key);
        return Files.exists(target) ? Optional.of(Files.readAllBytes(target)) : Optional.empty();
    }

    @Override
    public synchronized List<StoredEntry> list(String area, int limit) throws IOException {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        Path directory = areaPath(area);
        if (!Files.isDirectory(directory) || limit == 0) {
            return List.of();
        }
        List<Path> paths;
        try (var stream = Files.list(directory)) {
            paths = stream.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(limit)
                    .toList();
        }
        var entries = new ArrayList<StoredEntry>(paths.size());
        for (Path path : paths) {
            String filename = path.getFileName().toString();
            entries.add(new StoredEntry(filename.substring(0, filename.length() - 4), Files.readAllBytes(path)));
        }
        return List.copyOf(entries);
    }

    @Override
    public synchronized void remove(String area, String key) throws IOException {
        Files.deleteIfExists(entryPath(area, key));
    }

    private Path entryPath(String area, String key) {
        requireSafe(key, "key");
        return areaPath(area).resolve(key + ".bin");
    }

    private Path areaPath(String area) {
        requireSafe(area, "area");
        return root.resolve(area);
    }

    private static void requireSafe(String value, String label) {
        if (value == null || !SAFE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe " + label + ": " + value);
        }
    }
}
