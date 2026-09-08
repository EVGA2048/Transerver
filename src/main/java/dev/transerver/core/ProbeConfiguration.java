package dev.transerver.core;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record ProbeConfiguration(
        String nodeId,
        URI routerUri,
        Path dataDirectory,
        byte[] networkSecret,
        Duration pollInterval
) {
    public ProbeConfiguration {
        networkSecret = networkSecret.clone();
    }

    @Override
    public byte[] networkSecret() {
        return networkSecret.clone();
    }

    public static ProbeConfiguration load(Path file) throws IOException {
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String nodeId = required(properties, "nodeId");
        URI routerUri = URI.create(required(properties, "routerUrl"));
        if (!(routerUri.getScheme().equals("http") || routerUri.getScheme().equals("https"))) {
            throw new IllegalArgumentException("routerUrl must use http or https");
        }
        Path parent = file.toAbsolutePath().normalize().getParent();
        Path configuredData = Path.of(properties.getProperty("dataDirectory", "transerver-node").trim());
        Path dataDirectory = configuredData.isAbsolute() ? configuredData : parent.resolve(configuredData).normalize();
        String secret = required(properties, "networkSecret");
        if (secret.startsWith("REPLACE_") || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("networkSecret must be replaced with at least 32 bytes");
        }
        long pollMillis = parsePollMillis(properties.getProperty("pollMillis", "500"));
        return new ProbeConfiguration(nodeId, routerUri, dataDirectory,
                secret.getBytes(StandardCharsets.UTF_8), Duration.ofMillis(pollMillis));
    }

    private static long parsePollMillis(String value) {
        try {
            long result = Long.parseLong(value.trim());
            if (result < 50 || result > 60_000) {
                throw new IllegalArgumentException("pollMillis must be between 50 and 60000");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pollMillis must be an integer", exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration value: " + key);
        }
        return value.trim();
    }
}
