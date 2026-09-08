package dev.transerver.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WireBatchCodec {
    private static final int MAX_BATCH_SIZE = 128;
    private static final int MAX_ENVELOPE_BYTES = EnvelopeCodec.MAX_PAYLOAD_BYTES + 16 * 1024;
    private static final int MAX_RECEIPT_BYTES = 16 * 1024;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();

    byte[] encodeMessages(List<MessageEnvelope> messages) throws IOException {
        var encoded = new ArrayList<byte[]>(messages.size());
        for (MessageEnvelope message : messages) {
            encoded.add(envelopeCodec.encode(message));
        }
        return encodeBatch(encoded);
    }

    List<MessageEnvelope> decodeMessages(byte[] data) throws IOException {
        var result = new ArrayList<MessageEnvelope>();
        for (byte[] item : decodeBatch(data, MAX_ENVELOPE_BYTES)) {
            result.add(envelopeCodec.decode(item));
        }
        return List.copyOf(result);
    }

    byte[] encodeReceipts(List<FinalReceipt> receipts) throws IOException {
        var encoded = new ArrayList<byte[]>(receipts.size());
        for (FinalReceipt receipt : receipts) {
            encoded.add(receiptCodec.encode(receipt));
        }
        return encodeBatch(encoded);
    }

    List<FinalReceipt> decodeReceipts(byte[] data) throws IOException {
        var result = new ArrayList<FinalReceipt>();
        for (byte[] item : decodeBatch(data, MAX_RECEIPT_BYTES)) {
            result.add(receiptCodec.decode(item));
        }
        return List.copyOf(result);
    }

    private static byte[] encodeBatch(List<byte[]> items) throws IOException {
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IOException("Batch contains too many entries");
        }
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(items.size());
            for (byte[] item : items) {
                output.writeInt(item.length);
                output.write(item);
            }
        }
        return buffer.toByteArray();
    }

    private static List<byte[]> decodeBatch(byte[] data, int maxItemBytes) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(data))) {
            int count = input.readInt();
            if (count < 0 || count > MAX_BATCH_SIZE) {
                throw new IOException("Invalid batch size: " + count);
            }
            var items = new ArrayList<byte[]>(count);
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length < 0 || length > maxItemBytes) {
                    throw new IOException("Invalid batch entry length: " + length);
                }
                byte[] item = input.readNBytes(length);
                if (item.length != length) {
                    throw new IOException("Truncated batch entry");
                }
                items.add(item);
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing batch data");
            }
            return List.copyOf(items);
        }
    }
}
