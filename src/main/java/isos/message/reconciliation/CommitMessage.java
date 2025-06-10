package isos.message.reconciliation;

import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

public class CommitMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int viewNumber;
  private int replicaId; // r_i: Replica ID of sender
  private String depVerifiesHash;

  public CommitMessage() {
    super();
    this.msgType = ISOSMessageType.REC_COMMIT;
  }
}
