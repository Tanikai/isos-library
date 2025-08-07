package isos.consensus.model;

import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class DepVerifyMap {

  /**
   * v[f_i]: DepVerify for slot s_j from follower f_i
   *
   * <p>This map only contains DepVerify messages received from followers that are defined in the
   * follower quorum F of the depPropose message.
   */
  private Map<ReplicaId, DepVerifyMessage> depVerifies;

  private boolean isHashDirty;
  private String hash;

  public DepVerifyMap() {
    this.depVerifies = new HashMap<>();
    this.isHashDirty = false;
  }

  /**
   * Sorts the depVerify messages by their followerId (sequence numbers have to be the same) and
   * then calculates the SHA-256 hash.
   *
   * @param depVerifies List of DepVerify messages from the follower quorum defined in the initial
   *     DepPropose message.
   * @return The hash of the DepVerify message vector.
   */
  public static String calculateDepVerifyHash(Collection<DepVerifyMessage> depVerifies) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<DepVerifyMessage> sorted = new ArrayList<>(depVerifies);
      sorted.sort(Comparator.comparing(DepVerifyMessage::followerId));

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

  public Map<ReplicaId, DepVerifyMessage> getDepVerifies() {
    return Collections.unmodifiableMap(depVerifies);
  }

  public void setDepVerify(ReplicaId replicaId, DepVerifyMessage depVerify) {
    this.depVerifies.put(replicaId, depVerify);
    this.isHashDirty = true;
  }

  /**
   * Because we return an unmodifiable map of records, the hash can only change if {@link
   * #setDepVerify(ReplicaId, DepVerifyMessage)} is called. This means that we can cache the hash
   * and only re-calculate it if it was changed.
   *
   * @return
   */
  public String getdepVerifyHashCached() {
    if (isHashDirty) {
      this.hash = calculateDepVerifyHash(this.depVerifies.values());
      this.isHashDirty = false;
    }

    return this.hash;
  }
}
