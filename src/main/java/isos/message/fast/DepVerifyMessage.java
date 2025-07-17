package isos.message.fast;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

  public static DependencySet unionOfDependencies(List<DepVerifyMessage> depVerifies) {
    var allDeps =
        depVerifies.stream()
            .flatMap(m -> m.depSet().dependencies().stream())
            .collect(Collectors.toSet());
    return new DependencySet(allDeps);
  }

  /**
   * Sorts the depVerify messages by their sequence number and then calculates the SHA-256 hash.
   *
   * @param depVerifies List of DepVerify messages from the follower quorum defined in the initial
   *     DepPropose message.
   * @return The hash of the DepVerify message vector.
   */
  public static String calculateDepVerifyHash(List<DepVerifyMessage> depVerifies) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<DepVerifyMessage> sorted = new ArrayList<>(depVerifies);
      sorted.sort(Comparator.comparing(DepVerifyMessage::seqNum));

      for (DepVerifyMessage msg : sorted) {
        SequenceNumber seqNum = msg.seqNum();
        digest.update(ByteBuffer.allocate(4).putInt(seqNum.replicaId()).array());
        digest.update(ByteBuffer.allocate(4).putInt(seqNum.sequenceCounter()).array());
        ReplicaId followerId = msg.followerId();
        digest.update(ByteBuffer.allocate(4).putInt(followerId.value()).array());
        String depProposeHash = msg.depProposeHash();
        if (depProposeHash != null) {
          byte[] hashBytes = depProposeHash.getBytes(StandardCharsets.UTF_8);
          digest.update(ByteBuffer.allocate(4).putInt(hashBytes.length).array());
          digest.update(hashBytes);
        } else {
          digest.update(ByteBuffer.allocate(4).putInt(0).array());
        }
        digest.update(msg.depSet().asBytes());
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
}
