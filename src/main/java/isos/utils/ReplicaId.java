package isos.utils;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public record ReplicaId(int value) implements Serializable, Comparable<ReplicaId> {
  // TODO Kai: make configurable
  private static final ReplicaIdStrategy instanceStrategy = new ReplicaIdCacheMapStrategy();

  @Override
  public int compareTo(ReplicaId o) {
    return Integer.compare(this.value, o.value());
  }

  @Override
  public String toString() {
    return "ReplicaId(" + this.value + ")";
  }

  /**
   * Factory method for ReplicaId instances. Allows for cached maps.
   *
   * @param value
   * @return
   */
  public static ReplicaId of(int value) {
    return instanceStrategy.getInstance(value);
  }

  // Instance strategies for ReplicaIds

  private interface ReplicaIdStrategy {
    ReplicaId getInstance(int value);
  }

  private static class ReplicaIdNewInstanceStrategy implements ReplicaIdStrategy {
    @Override
    public ReplicaId getInstance(int value) {
      return new ReplicaId(value);
    }
  }

  private static class ReplicaIdCacheMapStrategy implements ReplicaIdStrategy {
    private final ConcurrentMap<Integer, ReplicaId> replicaIdCache = new ConcurrentHashMap<>();

    @Override
    public ReplicaId getInstance(int value) {
      return replicaIdCache.computeIfAbsent(value, ReplicaId::new);
    }
  }
}
