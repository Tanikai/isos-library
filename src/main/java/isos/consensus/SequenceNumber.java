package isos.consensus;

import isos.utils.NotImplementedException;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * A sequence number used to index an agreement slot. Requirements:
 *
 * <ul>
 *   <li>Comparable (total order)
 * </ul>
 */
public record SequenceNumber(int replicaId, int sequenceCounter)
    implements Externalizable, Comparable<SequenceNumber>, Cloneable {

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
  public void writeExternal(ObjectOutput out) throws IOException {}

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}

  @Override
  public Object clone() throws CloneNotSupportedException {
    throw new NotImplementedException();
  }
}
