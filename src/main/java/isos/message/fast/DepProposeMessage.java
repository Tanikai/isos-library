package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
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

  public String calculateHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      // update digest with contents
      if (seqNum != null) {
        digest.update(seqNum.toString().getBytes());
        digest.update(
            ByteBuffer.allocate(8)
                .putInt(seqNum.replicaId())
                .putInt(seqNum.sequenceCounter())
                .array());
      }
      if (coordinatorId != null) {
        digest.update(ByteBuffer.allocate(4).putInt(coordinatorId.value()).array());
      }
      if (requestHash != null) {
        digest.update(requestHash.getBytes());
      }
      if (depSet != null) {
        // INTEGER_SIZE * 2 (replicaId, seqCounter) * size
        var dependencies = depSet.dependencies();
        var buf = ByteBuffer.allocate(4 * 2 * dependencies.size());
        for (var dep : dependencies.stream().sorted().toList()) {
          buf.putInt(dep.replicaId());
          buf.putInt(dep.sequenceCounter());
        }
        digest.update(buf.array());
      }
      if (followerQuorum != null) {
        var buf = ByteBuffer.allocate(4 * followerQuorum.size());
        for (var followerId : followerQuorum.stream().sorted().toList()) {
          buf.putInt(followerId.value());
        }
        digest.update(buf.array());
      }
      byte[] hashBytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

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
    return Objects.equals(seqNum, that.seqNum)
        && Objects.equals(coordinatorId, that.coordinatorId)
        && Objects.equals(requestHash, that.requestHash)
        && Objects.equals(depSet, that.depSet)
        && Objects.equals(followerQuorum, that.followerQuorum);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seqNum, coordinatorId, requestHash, depSet, followerQuorum);
  }
}
