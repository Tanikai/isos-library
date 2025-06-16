package isos.message.reconciliation;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

public class PrepareMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ViewNumber viewNumber; // v_s_i: Agreement slot-specific view number; initial value -1
  private ReplicaId replicaId; // r_i: Replica ID of sender
  private String depVerifiesHash;

  public PrepareMessage() {
    super();
    this.msgType = ISOSMessageType.REC_PREPARE;
  }

  public PrepareMessage(
      ReplicaId senderID,
      SequenceNumber seqNum,
      ViewNumber viewNumber,
      ReplicaId replicaId,
      String depVerifiesHash) {
    super();
    this.msgType = ISOSMessageType.REC_COMMIT;
    this.sender = senderID.value();
    this.seqNum = seqNum;
    this.viewNumber = viewNumber;
    this.replicaId = replicaId;
    this.depVerifiesHash = depVerifiesHash;
  }
}
