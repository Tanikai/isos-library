package isos.message.replica.fast;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;

/**
 * @param seqNum agreement slot
 * @param replicaId Replica ID of sender
 * @param depVerifysHash Hash of DepVerifys
 */
public record DepCommitMessage(SequenceNumber seqNum, ReplicaId replicaId, String depVerifysHash)
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
