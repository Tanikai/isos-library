package isos.message.replica.viewchange;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import isos.consensus.model.viewchange.ViewChangeCertificate;
import java.io.Serializable;

/**
 * @param seqNum agreement slot
 * @param viewNumber New view number
 * @param replicaId Replica ID of sender
 * @param certificate Describes in what state the agreement slot was prior to view
 */
public record NewViewMessage(
    SequenceNumber seqNum,
    ViewNumber viewNumber,
    ReplicaId replicaId,
    ViewChangeCertificate certificate)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_NEWVIEW;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
