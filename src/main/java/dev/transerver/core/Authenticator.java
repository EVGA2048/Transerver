package dev.transerver.core;

public interface Authenticator {
    AuthProof sign(String nodeId, String method, String target, byte[] body);

    boolean verify(AuthProof proof, String method, String target, byte[] body);

    record AuthProof(String nodeId, long timestamp, String nonce, String signature) {
    }
}
