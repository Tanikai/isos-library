package isos.message.viewchange;

import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

public class ViewChangeMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int viewNumber;
  private int

  public ViewChangeMessage() {
    super();
    this.msgType = ISOSMessageType.VC_VIEWCHANGE;
  }
}
