package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;

/**
 * @param sender physical sender of this message
 * @param seqNum agreement slot
 * @param followerId Follower ID
 * @param depProposeHash Hash of the DepPropose msg that this DepVerify refers to
 * @param depSet dependency set determined by follower
 */
public record DepVerifyMessage(
    int sender,
    SequenceNumber seqNum,
    ReplicaId followerId,
    String depProposeHash,
    DependencySet depSet)
    implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_VERIFY;
  }
}
