package isos.message.fast;

import isos.consensus.DependencySet;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;

import java.util.Set;

public class DepProposeMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private int coordinatorId; // co: coordinator ID
  private String requestHash; // h(r): Hash of client request
  private DependencySet depSet; // D: dependency set determined by coordinator
  private Set<Integer> quorum; // F: Quorum containing IDs of 2f followers with lowest communication delay

  public DepProposeMessage() {
    super();
    this.msgType = ISOSMessageType.DEP_PROPOSE;
  }
}
