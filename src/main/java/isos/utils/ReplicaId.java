package isos.utils;

import java.io.Serializable;

public record ReplicaId(int value) implements Serializable, Comparable<ReplicaId> {

  @Override
  public int compareTo(ReplicaId o) {
    return Integer.compare(this.value, o.value());
  }
}
