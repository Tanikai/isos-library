package isos.message.replica.reconciliation;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.ISOSMessageWithViewNumber;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.io.Serializable;

/**
 * @param seqNum agreement slot
 * @param viewNumber agreement slot-specific view number ->
 * @param replicaId Replica ID of sender
 * @param depVerifysHash Hash of DepVerifys
 */
public record PrepareMessage(
    SequenceNumber seqNum, ViewNumber viewNumber, ReplicaId replicaId, String depVerifysHash)
    implements ISOSMessageWithViewNumber, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.REC_PREPARE;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
