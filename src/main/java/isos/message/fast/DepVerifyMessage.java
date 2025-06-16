package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;

import java.util.Objects;

public class DepVerifyMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ReplicaId followerId; // f_i: Follower ID
  private String depProposeHash; // h(dp): Hash of the DepPropose msg that this DepVerify refers to
  private DependencySet depSet; // D_i: dependency set determined by follower

  public DepVerifyMessage(
      ReplicaId senderId,
      SequenceNumber seqNum,
      ReplicaId followerId,
      String depProposeHash,
      DependencySet depSet) {
    super();
    this.msgType = ISOSMessageType.DEP_VERIFY;
    this.sender = senderId.value();
    this.seqNum = seqNum;
    this.followerId = followerId;
    this.depProposeHash = depProposeHash;
    this.depSet = depSet;
  }
}
