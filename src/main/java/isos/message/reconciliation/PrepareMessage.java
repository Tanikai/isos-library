package isos.message.reconciliation;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

/**
 * @param sender physical sender of this message
 * @param seqNum agreement slot
 * @param viewNumber agreement slot-specific view number
 * @param replicaId Replica ID of sender
 * @param depVerifiesHash Hash of DepVerifies
 */
public record PrepareMessage(
    int sender,
    SequenceNumber seqNum,
    ViewNumber viewNumber,
    ReplicaId replicaId,
    String depVerifiesHash)
    implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.REC_PREPARE;
  }
}
