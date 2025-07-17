package isos.consensus.model;

import isos.utils.ReplicaId;

import java.io.Serializable;

/**
 * A sequence number used to index an agreement slot. Requirements:
 *
 * <ul>
 *   <li>Comparable (total order)
 * </ul>
 */
public record SequenceNumber(int replicaId, int sequenceCounter)
    implements Comparable<SequenceNumber>, Serializable {

  public SequenceNumber(ReplicaId replicaId, int sequenceCounter) {
    this(replicaId.value(), sequenceCounter);
  }

  /**
   * Returns a replica ID record instead of int
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
    return new SequenceNumber(current.replicaId(), current.sequenceCounter() + 1);
  }

  public static SequenceNumber prevSequenceNumber(SequenceNumber current) throws IllegalArgumentException {
    if (current.sequenceCounter() == 0) {
      throw new IllegalArgumentException("SequenceNumber below 0 is not possible");
    }

    return new SequenceNumber(current.replicaId(), current.sequenceCounter() - 1);
  }
}
