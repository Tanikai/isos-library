package isos.message.replica.viewchange;

import isos.consensus.model.SequenceNumber;
import isos.consensus.model.viewchange.ViewChangeCertificate;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.ISOSMessageWithViewNumber;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.io.Serializable;

/**
 * @param seqNum Agreement slot
 * @param viewNumber New view number of the agreement slot
 * @param replicaId Sender of message that moved to new view (not the new coordinator!)
 * @param certificate View change certificate, or null
 */
public record ViewChangeMessage(
    SequenceNumber seqNum,
    ViewNumber viewNumber,
    ReplicaId replicaId,
    ViewChangeCertificate certificate)
    implements ISOSMessageWithViewNumber, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_VIEWCHANGE;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
