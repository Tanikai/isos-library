package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;

import java.util.Set;

public class DepProposeMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ReplicaId coordinatorId; // co: coordinator ID
  private String requestHash; // h(r): Hash of client request
  private DependencySet depSet; // D: dependency set determined by coordinator
  private Set<ReplicaId>
      followerQuorum; // F: Quorum containing IDs of 2f followers with lowest communication delay

  public DepProposeMessage(
      ReplicaId senderId, // physical sender of this message
      SequenceNumber seqNum,
      ReplicaId coordinatorId,
      String requestHash,
      DependencySet depSet,
      Set<ReplicaId> followerQuorum) {
    super();
    this.msgType = ISOSMessageType.DEP_PROPOSE;
    this.sender = senderId.value();
    this.seqNum = seqNum;
    this.coordinatorId = coordinatorId;
    this.requestHash = requestHash;
    this.depSet = depSet;
    this.followerQuorum = followerQuorum;
  }
}
