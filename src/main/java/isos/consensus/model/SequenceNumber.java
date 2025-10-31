package isos.consensus.model;

import isos.utils.ReplicaId;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A sequence number used to index an agreement slot. Requirements:
 *
 * <ul>
 *   <li>Comparable (total order)
 * </ul>
 */
public record SequenceNumber(int replicaId, int sequenceCounter)
    implements Comparable<SequenceNumber>, Serializable {
  // Potential Issue: this strategy is only used when the developer needs a new sequence number
  // instance. When a new SequenceNumber is created during deserialization of received messages,
  // a new instance is created.
  private static SequenceNumberStrategy instanceStrategy = new SequenceNumberNewInstanceStrategy();

  public SequenceNumber(ReplicaId replicaId, int sequenceCounter) {
    this(replicaId.value(), sequenceCounter);
  }

  /**
   * Helper method to return the replica ID of the sequence number as a record instead of int
   *
   * @return
   */
  public ReplicaId replicaIdRec() {
    return new ReplicaId(this.replicaId);
  }

  @Override
  public int compareTo(SequenceNumber o) {
    int cmp = Integer.compare(this.replicaId, o.replicaId);
    if (cmp != 0) {
      return cmp;
    }
    return Integer.compare(this.sequenceCounter, o.sequenceCounter);
  }

  @Override
  public String toString() {
    return this.replicaId + "." + this.sequenceCounter;
  }

  public static SequenceNumber nextSequenceNumber(SequenceNumber current) {
    return SequenceNumber.of(current.replicaId(), current.sequenceCounter() + 1);
  }

  public static SequenceNumber prevSequenceNumber(SequenceNumber current)
      throws IllegalArgumentException {
    if (current.sequenceCounter() == 0) {
      throw new IllegalArgumentException("SequenceNumber below 0 is not possible");
    }

    return SequenceNumber.of(current.replicaId(), current.sequenceCounter() - 1);
  }

  // Instance strategies for Sequence Numbers

  public static SequenceNumber of(ReplicaId replicaId, int sequenceNumber) {
    return instanceStrategy.getInstance(replicaId.value(), sequenceNumber);
  }

  public static SequenceNumber of(int replicaId, int sequenceNumber) {
    return instanceStrategy.getInstance(replicaId, sequenceNumber);
  }

  public static void setInstanceStrategy(SequenceNumberStrategy newStrategy) {
    instanceStrategy = newStrategy;
  }

  public interface SequenceNumberStrategy {
    SequenceNumber getInstance(int replicaId, int sequenceNumber);
  }

  public static class SequenceNumberNewInstanceStrategy implements SequenceNumberStrategy {
    @Override
    public SequenceNumber getInstance(int replicaId, int sequenceNumber) {
      return new SequenceNumber(replicaId, sequenceNumber);
    }
  }

  public static class SequenceNumberCacheMapStrategy implements SequenceNumberStrategy {
    private final ConcurrentMap<Long, SequenceNumber> sequenceNumberCache =
        new ConcurrentHashMap<>();

    @Override
    public SequenceNumber getInstance(int replicaId, int sequenceNumber) {
      // because we have two values, we need to create a composite key
      // see https://stackoverflow.com/questions/12772939/java-storing-two-ints-in-a-long
      long compositeKey = ((long) replicaId << 32 | (sequenceNumber & 0xffffffffL));
      return sequenceNumberCache.computeIfAbsent(
          compositeKey, x -> new SequenceNumber(replicaId, sequenceNumber));
    }
  }
}
