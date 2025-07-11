package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ClientRequest;
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
    Set<ReplicaId> followerQuorum,
    ClientRequest request)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_PROPOSE;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.coordinatorId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepProposeMessage that = (DepProposeMessage) o;
    return java.util.Objects.equals(seqNum, that.seqNum)
        && java.util.Objects.equals(coordinatorId, that.coordinatorId)
        && java.util.Objects.equals(requestHash, that.requestHash)
        && java.util.Objects.equals(depSet, that.depSet)
        && java.util.Objects.equals(followerQuorum, that.followerQuorum)
        && java.util.Objects.equals(request, that.request);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(seqNum, coordinatorId, requestHash, depSet, followerQuorum, request);
  }
}
