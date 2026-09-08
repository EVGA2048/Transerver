package dev.transerver.api;

import java.util.Optional;

/** Stable service entry point used by mods that depend on Transerver. */
public final class TranserverServices {
    private static volatile TranserverApi current;

    public static Optional<TranserverApi> api() {
        return Optional.ofNullable(current);
    }

    /** Runtime hook. Integrations should only call {@link #api()}. */
    public static synchronized void install(TranserverApi api) {
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        if (current != null && current != api) {
            throw new IllegalStateException("A Transerver runtime is already installed");
        }
        current = api;
    }

    /** Runtime hook used during orderly server shutdown. */
    public static synchronized void clear(TranserverApi api) {
        if (current == api) {
            current = null;
        }
    }

    private TranserverServices() {
    }
}
