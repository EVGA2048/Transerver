package dev.transerver.core;

import dev.transerver.api.DeliveryState;

import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface Transport {
    Set<String> knownNodes(String nodeId);

    DeliveryState relay(MessageEnvelope message);

    List<MessageEnvelope> receive(String nodeId, int limit);

    void submitReceipt(FinalReceipt receipt);

    List<FinalReceipt> receiveReceipts(String nodeId, int limit);

    void acknowledgeReceipt(String nodeId, UUID messageId);
}
