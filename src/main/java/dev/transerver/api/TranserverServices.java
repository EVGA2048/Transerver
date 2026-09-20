package dev.transerver.api;

import java.util.Optional;

/** Stable service entry point used by mods that depend on Transerver. */
public final class TranserverServices {
    private static volatile TranserverApi current;
    private static volatile NodeIdentity identity;

    public static Optional<TranserverApi> api() {
        return Optional.ofNullable(current);
    }

    public static Optional<NodeIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    /**
     * Publishes the stable node identity independently of the transport runtime.
     *
     * <p>A node still has an identity while transport is disabled or misconfigured. Integrations
     * use that identity for durable local references and must not have to invent a fake UUID just
     * because {@link #api()} is currently empty.
     */
    public static synchronized void installIdentity(NodeIdentity nodeIdentity) {
        if (nodeIdentity == null) {
            throw new IllegalArgumentException("nodeIdentity must not be null");
        }
        if (identity != null && !identity.nodeId().equals(nodeIdentity.nodeId())) {
            throw new IllegalStateException("A different Transerver node identity is already installed");
        }
        identity = nodeIdentity;
    }

    /** Runtime hook. Integrations should only call {@link #api()}. */
    public static synchronized void install(TranserverApi api, NodeIdentity nodeIdentity) {
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        if (nodeIdentity == null) {
            throw new IllegalArgumentException("nodeIdentity must not be null");
        }
        if (current != null && current != api) {
            throw new IllegalStateException("A Transerver runtime is already installed");
        }
        installIdentity(nodeIdentity);
        current = api;
    }

    /** Runtime hook used during orderly server shutdown. */
    public static synchronized void clear(TranserverApi api) {
        if (current == api) {
            current = null;
        }
    }

    /** Runtime hook used when the Minecraft server itself is stopping. */
    public static synchronized void clearIdentity(NodeIdentity nodeIdentity) {
        if (nodeIdentity != null && identity != null && identity.nodeId().equals(nodeIdentity.nodeId())) {
            identity = null;
        }
    }

    private TranserverServices() {
    }
}
