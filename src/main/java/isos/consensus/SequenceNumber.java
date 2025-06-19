package isos.consensus;

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
}
