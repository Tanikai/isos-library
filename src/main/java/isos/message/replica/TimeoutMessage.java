package isos.message.replica;

import isos.consensus.model.ISOSTimeoutType;
import isos.consensus.model.SequenceNumber;
import isos.utils.ReplicaId;

public record TimeoutMessage(ISOSTimeoutType timeoutType, SequenceNumber seqNum, ReplicaId ownReplicaId) implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.TIMEOUT;
  }

  @Override
  public ReplicaId logicalSender() {
    return ownReplicaId;
  }
}
