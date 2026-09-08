package dev.transerver.core;

import dev.transerver.api.CompletedSend;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class CompletedSendCodec {
    private static final int MAGIC = 0x54534353;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final ReceiptCodec receiptCodec = new ReceiptCodec();

    byte[] encode(MessageEnvelope message, FinalReceipt receipt) throws IOException {
        byte[] encodedMessage = envelopeCodec.encode(message);
        byte[] encodedReceipt = receiptCodec.encode(receipt);
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(encodedMessage.length);
            output.write(encodedMessage);
            output.write(encodedReceipt);
        }
        return buffer.toByteArray();
    }

    CompletedSend decode(byte[] encoded) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Not a completed send");
            }
            int messageLength = input.readInt();
            int maximum = EnvelopeCodec.MAX_PAYLOAD_BYTES + 16 * 1024;
            if (messageLength < 0 || messageLength > maximum) {
                throw new IOException("Invalid completed-send message length");
            }
            byte[] messageBytes = input.readNBytes(messageLength);
            if (messageBytes.length != messageLength) {
                throw new IOException("Truncated completed-send message");
            }
            byte[] receiptBytes = input.readAllBytes();
            MessageEnvelope message = envelopeCodec.decode(messageBytes);
            FinalReceipt receipt = receiptCodec.decode(receiptBytes);
            if (!message.messageId().equals(receipt.messageId())
                    || !message.source().equals(receipt.source())
                    || !message.destination().equals(receipt.destination())) {
                throw new IOException("Completed-send receipt does not match message");
            }
            return new CompletedSend(message.messageId(), message.channel(), message.destination(),
                    message.correlationId(), message.payload(), receipt.state(), receipt.detail(),
                    receipt.completedAt());
        }
    }
}
