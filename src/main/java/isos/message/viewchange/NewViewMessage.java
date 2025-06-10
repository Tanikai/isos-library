package isos.message.viewchange;

import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.viewchange.ViewChangeCertificate;

public class NewViewMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int viewNumber; // v_s_i: Agreement slot-specific view number
  private int replicaId; // r_i: Replica ID of sender
  private ViewChangeCertificate
      certificate; // certificate: Describes in what state the agreement slot was prior to view
                   // change

  public NewViewMessage() {
    super();
    this.msgType = ISOSMessageType.VC_NEWVIEW;
  }
}
