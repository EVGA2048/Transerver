package dev.transerver.neoforge;

import dev.transerver.api.NodeIdentity;
import dev.transerver.api.TranserverApi;
import dev.transerver.api.TranserverServices;
import dev.transerver.core.FileMessageStore;
import dev.transerver.core.HmacAuthenticator;
import dev.transerver.core.HttpTransport;
import dev.transerver.core.NodeIdentityFile;
import dev.transerver.core.RouteResolver;
import dev.transerver.core.TranserverNode;
import dev.transerver.core.TranserverRuntime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@EventBusSubscriber(modid = TranserverMod.MOD_ID)
public final class TranserverServerLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(TranserverMod.MOD_ID);
    private static TranserverRuntime runtime;
    private static TranserverApi api;

    @SubscribeEvent
    public static synchronized void started(ServerStartedEvent event) {
        if (!TranserverNeoForgeConfig.ENABLED.get()) {
            LOG.info("Transerver node is disabled");
            return;
        }
        if (runtime != null) {
            return;
        }
        try {
            byte[] secret = validateSecret(TranserverNeoForgeConfig.NETWORK_SECRET.get());
            URI router = validateRouter(TranserverNeoForgeConfig.ROUTER_URL.get());
            Path root = FMLPaths.GAMEDIR.get().resolve("transerver").toAbsolutePath().normalize();
            NodeIdentityFile identityFile = new NodeIdentityFile(root.resolve("node-identity.properties"));
            NodeIdentity identity = identityFile.loadOrCreate(TranserverNeoForgeConfig.NODE_ALIAS.get());
            if (!identity.alias().equals(TranserverNeoForgeConfig.NODE_ALIAS.get().trim())) {
                identity = identityFile.rename(TranserverNeoForgeConfig.NODE_ALIAS.get());
            }
            String nodeId = identity.nodeId().toString();
            RouteResolver routes = ignored -> Optional.of(router);
            var node = new TranserverNode(nodeId,
                    new HttpTransport(nodeId, routes, new HmacAuthenticator(secret)),
                    new FileMessageStore(root.resolve("messages")));
            var nodeRuntime = new TranserverRuntime(node,
                    Duration.ofMillis(TranserverNeoForgeConfig.POLL_MILLIS.get()));
            TranserverServices.install(node);
            api = node;
            runtime = nodeRuntime;
            nodeRuntime.start();
            LOG.info("Transerver node started: {} ({}) via {}", identity.alias(), identity.fingerprint(), router);
        } catch (Exception exception) {
            LOG.error("Transerver node could not start; networking remains disabled", exception);
            stopRuntime();
        }
    }

    @SubscribeEvent
    public static synchronized void stopping(ServerStoppingEvent event) {
        stopRuntime();
    }

    private static void stopRuntime() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
        if (api != null) {
            TranserverServices.clear(api);
            api = null;
        }
    }

    private static byte[] validateSecret(String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || value == null || value.startsWith("REPLACE_")) {
            throw new IllegalArgumentException("networkSecret must be replaced with at least 32 UTF-8 bytes");
        }
        return bytes;
    }

    private static URI validateRouter(String value) {
        URI uri = URI.create(value == null ? "" : value.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("routerUrl must use http or https");
        }
        return uri;
    }

    private TranserverServerLifecycle() {
    }
}
