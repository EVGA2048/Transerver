package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public final class ReceiptCodec {
    private static final int MAGIC = 0x54535243;
    private static final int MAX_TEXT_BYTES = 4096;

    public byte[] encode(FinalReceipt receipt) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeLong(receipt.messageId().getMostSignificantBits());
            output.writeLong(receipt.messageId().getLeastSignificantBits());
            writeText(output, receipt.source());
            writeText(output, receipt.destination());
            output.writeByte(receipt.state().ordinal());
            writeText(output, receipt.detail() == null ? "" : receipt.detail());
            output.writeLong(receipt.completedAt().toEpochMilli());
        }
        return buffer.toByteArray();
    }

    public FinalReceipt decode(byte[] encoded) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Not a Transerver receipt");
            }
            UUID id = new UUID(input.readLong(), input.readLong());
            String source = readText(input);
            String destination = readText(input);
            int stateIndex = input.readUnsignedByte();
            DeliveryState[] states = DeliveryState.values();
            if (stateIndex >= states.length) {
                throw new IOException("Invalid receipt state");
            }
            String detail = readText(input);
            Instant completedAt = Instant.ofEpochMilli(input.readLong());
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing receipt data");
            }
            return new FinalReceipt(id, source, destination, states[stateIndex], detail, completedAt);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid receipt", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IOException("Receipt text is too long");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IOException("Invalid receipt text length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated receipt text");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
