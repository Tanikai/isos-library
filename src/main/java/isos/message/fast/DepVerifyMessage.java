package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;

/**
 * @param seqNum agreement slot
 * @param followerId Follower ID
 * @param depProposeHash Hash of the DepPropose msg that this DepVerify refers to
 * @param depSet dependency set determined by follower
 */
public record DepVerifyMessage(
    SequenceNumber seqNum, ReplicaId followerId, String depProposeHash, DependencySet depSet)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_VERIFY;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.followerId;
  }
}
