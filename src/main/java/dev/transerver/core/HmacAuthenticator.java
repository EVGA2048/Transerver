package dev.transerver.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HmacAuthenticator implements Authenticator {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final Clock clock;
    private final long allowedSkewSeconds;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    public HmacAuthenticator(byte[] secret) {
        this(secret, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public HmacAuthenticator(byte[] secret, Clock clock, Duration allowedSkew) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Network secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
        this.clock = clock;
        this.allowedSkewSeconds = allowedSkew.toSeconds();
    }

    @Override
    public AuthProof sign(String nodeId, String method, String target, byte[] body) {
        long timestamp = clock.instant().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        return new AuthProof(nodeId, timestamp, nonce,
                signature(nodeId, method, target, body, timestamp, nonce));
    }

    @Override
    public boolean verify(AuthProof proof, String method, String target, byte[] body) {
        if (proof == null || proof.nodeId() == null || proof.nonce() == null || proof.signature() == null) {
            return false;
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - proof.timestamp()) > allowedSkewSeconds) {
            return false;
        }
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < now - allowedSkewSeconds);
        String nonceKey = proof.nodeId() + '\0' + proof.nonce();
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(proof.signature());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        byte[] expected = Base64.getUrlDecoder().decode(
                signature(proof.nodeId(), method, target, body, proof.timestamp(), proof.nonce()));
        if (!MessageDigest.isEqual(supplied, expected)) {
            return false;
        }
        return seenNonces.putIfAbsent(nonceKey, proof.timestamp()) == null;
    }

    private String signature(String nodeId, String method, String target, byte[] body,
                             long timestamp, String nonce) {
        String bodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(body));
        String canonical = method + '\n' + target + '\n' + nodeId + '\n'
                + timestamp + '\n' + nonce + '\n' + bodyHash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate HMAC", exception);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
