package dev.transerver.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouterConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsStandaloneRouterConfiguration() throws Exception {
        Path file = temporaryDirectory.resolve("router.properties");
        Files.writeString(file, """
                bind=127.0.0.1
                port=9123
                dataDirectory=state
                nodes=alpha,beta,gamma
                networkSecret=0123456789abcdef0123456789abcdef
                """);

        RouterConfiguration configuration = RouterConfiguration.load(file);

        assertEquals(9123, configuration.listenAddress().getPort());
        assertEquals(Set.of("alpha", "beta", "gamma"), configuration.nodes());
        assertEquals(temporaryDirectory.resolve("state"), configuration.dataDirectory());
    }

    @Test
    void rejectsTemplateSecretAndSingleNode() throws Exception {
        Path file = temporaryDirectory.resolve("router.properties");
        Files.writeString(file, """
                nodes=alpha
                networkSecret=REPLACE_WITH_AT_LEAST_32_RANDOM_CHARACTERS
                """);

        assertThrows(IllegalArgumentException.class, () -> RouterConfiguration.load(file));
    }
}
