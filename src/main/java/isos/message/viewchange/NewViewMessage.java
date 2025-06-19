package isos.message.viewchange;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import isos.viewchange.ViewChangeCertificate;

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
    implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_NEWVIEW;
  }
}
