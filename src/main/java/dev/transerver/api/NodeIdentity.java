package dev.transerver.api;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A stable machine identity with a user-editable display name. */
public record NodeIdentity(UUID nodeId, String alias) {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    public NodeIdentity {
        Objects.requireNonNull(nodeId, "nodeId");
        alias = normalizeAlias(alias);
    }

    public NodeIdentity withAlias(String newAlias) {
        return new NodeIdentity(nodeId, newAlias);
    }

    /** Short display-only fingerprint. The full UUID remains authoritative. */
    public String fingerprint() {
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(nodeId.getMostSignificantBits())
                .putLong(nodeId.getLeastSignificantBits())
                .array();
        StringBuilder encoded = new StringBuilder(16);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5 && encoded.length() < 16) {
                bits -= 5;
                encoded.append(CROCKFORD[(buffer >>> bits) & 31]);
            }
            if (encoded.length() == 16) {
                break;
            }
        }
        return encoded.substring(0, 8) + "-" + encoded.substring(8).toUpperCase(Locale.ROOT);
    }

    private static String normalizeAlias(String value) {
        if (value == null) {
            throw new IllegalArgumentException("alias must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("alias must contain between 1 and 64 characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("alias must not contain control characters");
        }
        return normalized;
    }
}
