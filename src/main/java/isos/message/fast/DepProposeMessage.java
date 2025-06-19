package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;
import java.util.Set;

/**
 * @param seqNum agreement slot
 * @param coordinatorId coordinator ID
 * @param requestHash Hash of client request
 * @param depSet dependency set determined by coordinator
 * @param followerQuorum Quorum containing IDs of 2f followers with lowest communication delay
 */
public record DepProposeMessage(
    SequenceNumber seqNum,
    ReplicaId coordinatorId,
    String requestHash,
    DependencySet depSet,
    Set<ReplicaId> followerQuorum)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_PROPOSE;
  }
}
