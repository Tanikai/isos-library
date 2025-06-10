package isos.message.fast;

import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

public class DepCommitMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int replicaId; // r_i: Replica ID of sender
  private String depVerifiesHash; // h(vec{dv}): Hash of DepVerifies

  public DepCommitMessage() {
    super();
    this.msgType = ISOSMessageType.DEP_COMMIT;
  }
}
