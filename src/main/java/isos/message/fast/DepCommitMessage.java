package isos.message.fast;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;

/**
 * @param seqNum agreement slot
 * @param replicaId Replica ID of sender
 * @param depVerifiesHash Hash of DepVerifies
 */
public record DepCommitMessage(SequenceNumber seqNum, ReplicaId replicaId, String depVerifiesHash)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_COMMIT;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
