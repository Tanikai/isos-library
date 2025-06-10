package isos.message.fast;

import isos.consensus.DependencySet;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

public class DepVerifyMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int followerId; // f_i: Follower ID
  private String depProposeHash; // h(dp): Hash of the DepPropose msg that this DepVerify refers to
  private DependencySet depSet; // D_i: dependency set determined by follower

  public DepVerifyMessage() {
    super();
    this.msgType = ISOSMessageType.DEP_VERIFY;
  }
}
