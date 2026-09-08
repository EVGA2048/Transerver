package dev.transerver.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class EnvelopeCodec {
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x54535652;
    private static final int VERSION = 1;
    private static final int MAX_TEXT_BYTES = 1024;

    public byte[] encode(MessageEnvelope message) throws IOException {
        if (message.payload().length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            output.writeLong(message.messageId().getMostSignificantBits());
            output.writeLong(message.messageId().getLeastSignificantBits());
            writeText(output, message.channel());
            writeText(output, message.source());
            writeText(output, message.destination());
            writeNullableText(output, message.correlationId());
            output.writeLong(message.createdAt().toEpochMilli());
            output.writeLong(message.expiresAt() == null ? -1 : message.expiresAt().toEpochMilli());
            writeText(output, message.contentType());
            output.writeInt(message.payload().length);
            output.write(message.payload());
            output.write(sha256(message.payload()));
        }
        return buffer.toByteArray();
    }

    public MessageEnvelope decode(byte[] encoded) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Not a Transerver message");
            }
            int version = input.readUnsignedByte();
            if (version != VERSION) {
                throw new IOException("Unsupported protocol version: " + version);
            }
            UUID id = new UUID(input.readLong(), input.readLong());
            String channel = readText(input);
            String source = readText(input);
            String destination = readText(input);
            String correlationId = readNullableText(input);
            Instant createdAt = Instant.ofEpochMilli(input.readLong());
            long expiresAtMillis = input.readLong();
            Instant expiresAt = expiresAtMillis < 0 ? null : Instant.ofEpochMilli(expiresAtMillis);
            String contentType = readText(input);
            int payloadLength = input.readInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                throw new IOException("Invalid payload length: " + payloadLength);
            }
            byte[] payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("Truncated payload");
            }
            byte[] expectedHash = input.readNBytes(32);
            if (expectedHash.length != 32 || !MessageDigest.isEqual(expectedHash, sha256(payload))) {
                throw new IOException("Payload checksum mismatch");
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing message data");
            }
            return new MessageEnvelope(id, channel, source, destination, correlationId,
                    createdAt, expiresAt, contentType, payload);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid message value", exception);
        }
    }

    private static void writeNullableText(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
        } else {
            writeText(output, value);
        }
    }

    private static String readNullableText(DataInputStream input) throws IOException {
        int length = input.readInt();
        return length < 0 ? null : readText(input, length);
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IOException("Text field exceeds " + MAX_TEXT_BYTES + " bytes");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        return readText(input, input.readInt());
    }

    private static String readText(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IOException("Invalid text field length: " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated text field");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }
}
