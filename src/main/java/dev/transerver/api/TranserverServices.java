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
        current = api;
        identity = nodeIdentity;
    }

    /** Runtime hook used during orderly server shutdown. */
    public static synchronized void clear(TranserverApi api) {
        if (current == api) {
            current = null;
            identity = null;
        }
    }

    private TranserverServices() {
    }
}
