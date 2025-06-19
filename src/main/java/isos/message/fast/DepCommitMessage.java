package isos.message.fast;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;

/**
 * @param sender physical sender of this message
 * @param seqNum agreement slot
 * @param replicaId Replica ID of sender
 * @param depVerifiesHash Hash of DepVerifies
 */
public record DepCommitMessage(
    int sender,
    SequenceNumber seqNum,
    ReplicaId replicaId,
    String depVerifiesHash)
    implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_COMMIT;
  }
}
