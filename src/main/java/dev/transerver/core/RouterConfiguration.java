package dev.transerver.core;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public record RouterConfiguration(
        InetSocketAddress listenAddress,
        Path dataDirectory,
        Set<String> nodes,
        byte[] networkSecret
) {
    public RouterConfiguration {
        nodes = Set.copyOf(nodes);
        networkSecret = networkSecret.clone();
    }

    @Override
    public byte[] networkSecret() {
        return networkSecret.clone();
    }

    public static RouterConfiguration load(Path file) throws IOException {
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String bind = properties.getProperty("bind", "127.0.0.1").trim();
        int port = integer(properties, "port", 8765, 1, 65535);
        Path parent = file.toAbsolutePath().normalize().getParent();
        Path configuredData = Path.of(properties.getProperty("dataDirectory", "transerver-router").trim());
        Path dataDirectory = configuredData.isAbsolute() ? configuredData : parent.resolve(configuredData).normalize();
        Set<String> nodes = parseNodes(required(properties, "nodes"));
        String secret = required(properties, "networkSecret");
        if (secret.startsWith("REPLACE_") || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("networkSecret must be replaced with at least 32 bytes");
        }
        return new RouterConfiguration(
                new InetSocketAddress(InetAddress.getByName(bind), port),
                dataDirectory, nodes, secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> parseNodes(String value) {
        var nodes = new LinkedHashSet<String>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .forEach(nodes::add);
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("nodes must contain at least two unique server IDs");
        }
        return nodes;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration value: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, int fallback, int minimum, int maximum) {
        int value;
        try {
            value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
