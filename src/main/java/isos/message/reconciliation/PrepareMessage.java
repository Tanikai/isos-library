package isos.message.reconciliation;

import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

public class PrepareMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int viewNumber; // v_s_i: Agreement slot-specific view number; initial value -1
  private int replicaId; // r_i: Replica ID of sender
  private String depVerifiesHash;

  public PrepareMessage() {
    super();
    this.msgType = ISOSMessageType.REC_PREPARE;
  }
}
