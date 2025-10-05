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
  // TODO Kai: make configurable
  private static final SequenceNumberStrategy instanceStrategy =
      new SequenceNumberCacheMapStrategy();

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
    if (this.replicaId > o.replicaId) {
      return 1;
    } else if (this.replicaId < o.replicaId) {
      return -1;
    } else {
      // same replica
      if (this.sequenceCounter > o.sequenceCounter) {
        return 1;
      } else if (this.sequenceCounter < o.sequenceCounter) {
        return -1;
      } else {
        return 0;
      }
    }
  }

  @Override
  public String toString() {
    return String.format("%d.%d", this.replicaId, this.sequenceCounter);
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

  private interface SequenceNumberStrategy {
    SequenceNumber getInstance(int replicaId, int sequenceNumber);
  }

  private static class SequenceNumberNewInstanceStrategy implements SequenceNumberStrategy {
    @Override
    public SequenceNumber getInstance(int replicaId, int sequenceNumber) {
      return new SequenceNumber(replicaId, sequenceNumber);
    }
  }

  private static class SequenceNumberCacheMapStrategy implements SequenceNumberStrategy {
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
