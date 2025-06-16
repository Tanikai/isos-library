package isos.message.viewchange;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import isos.viewchange.ViewChangeCertificate;

public class NewViewMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ViewNumber viewNumber; // v_s_i: New view number
  private ReplicaId replicaId; // r_i: Replica ID of sender
  private ViewChangeCertificate
      certificate; // certificate: Describes in what state the agreement slot was prior to view

  public NewViewMessage(
      ReplicaId senderID,
      SequenceNumber seqNum,
      ViewNumber viewNumber,
      ReplicaId replicaId,
      ViewChangeCertificate certificate) {
    super();
    this.msgType = ISOSMessageType.VC_NEWVIEW;
    this.sender = senderID.value();
    this.seqNum = seqNum;
    this.viewNumber = viewNumber;
    this.replicaId = replicaId;
    this.certificate = certificate;
  }
}
