package isos.consensus.model;

import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class DepVerifyMap {

  /**
   * v[f_i]: DepVerify for slot s_j from follower f_i
   *
   * <p>This map only contains DepVerify messages received from followers that are defined in the
   * follower quorum F of the depPropose message.
   */
  private Map<ReplicaId, DepVerifyMessage> depVerifies;

  private Set<ReplicaId> followerQuroum = null;

  private boolean isHashDirty;
  private String hash;

  public DepVerifyMap() {
    this.depVerifies = new HashMap<>();
    this.isHashDirty = false;
  }

  public void setFollowerQuroum(Set<ReplicaId> followerQuroum) {
    if (this.followerQuroum != null) {
      throw new IllegalStateException("FollowerQuorum was already set");
    }
    this.followerQuroum = followerQuroum;
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

  public void setDepVerify(ReplicaId replicaId, DepVerifyMessage depVerify)
      throws IllegalArgumentException {
    if (!followerQuroum.contains(replicaId)) {
      throw new IllegalArgumentException(
          String.format("ReplicaId %s is not contained in the follower quorum.", replicaId));
    }
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

  public void clearDepVerifys() {
    this.depVerifies.clear();
  }

  public boolean reachedQuorum(int quorumSize) {
    return this.depVerifies.size() >= quorumSize;
  }

  /**
   * Checks whether the fp-verified predicate holds. When 2f DepVerify messages are present, we
   * check whether every dependency is reported by at least f+1 followers.
   *
   * <p>(Valid DepPropose and matching hash has to be checked as a precondition)
   *
   * <p>Paper page 12: Definition A.2. A slot s_j is verified if a correct replica collects a valid
   * DEPPROPOSE dp, 2f valid DEPVERIFYs from different replicas with matching h(dp) and each
   * DEPVERIFY is from a replica in the fast-path quorum dp.F.
   *
   * <p>Paper page 12: Definition A.3. A slot s_j is fp-verified if a correct replica verified it
   * and each dependency in the DEPVERIFYs occurs at least f + 1 times.
   *
   * <p>-> This means that the dependency set of the DepPropose is irrelevant for fp-verified.
   *
   * @param maxFaults
   * @return
   */
  public boolean isFpVerified(int maxFaults) {
    // Before we are fp-verified, we need the depVerifies from the 2f followers.
    if (!this.reachedQuorum(2 * maxFaults)) {
      return false;
    }

    // For fp-verified, we only need to check the dependencies of the DepVerify messages of the
    // followers. (see pseudocode line 47).
    // Every dependency has to be reported by at least f+1 followers.
    return DepVerifyMap.minimumDependencyOccurrence(this.depVerifies.values(), maxFaults + 1);
  }

  /**
   * Counts the occurrence of dependencies in each DepVerify message, and then checks whether every
   * dependency has an occurrence equal or greater than minCount. Used in {@link
   * #isFpVerified(int)}.
   *
   * @param minCount The minimum occurrence of each dependency.
   * @return True if every dependency appears in at least minCount dependency sets, false otherwise.
   *     If depVerifies is empty, true is always returned.
   */
  public static boolean minimumDependencyOccurrence(
      Collection<DepVerifyMessage> depVerifies, int minCount) {

    // First, we have to count the occurrence of each sequence number
    Map<SequenceNumber, Long> seqNumCounts =
        depVerifies.stream()
            // Get dependency set of each message as Set<SequenceNumber>
            .map(msg -> msg.depSet().dependencies())
            // Convert to a single stream of SequenceNumbers
            .flatMap(Set::stream)
            // Collect by SequenceNumber, with the occurrence as value
            // -> a dependency can only appear once in a single dependency set; thus, we are
            // effectively counting in how many dependency sets the sequence number appeared
            .collect(Collectors.groupingBy(seqNum -> seqNum, Collectors.counting()));

    // Check whether all dependencies appeared in at least minCount dependency sets
    return seqNumCounts.values().stream().allMatch((count) -> count >= minCount);
  }
}
