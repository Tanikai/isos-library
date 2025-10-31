package isos.utils;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public record ViewNumber(int value) implements Serializable, Comparable<ViewNumber> {
  private static ViewNumberStrategy instanceStrategy = new ViewNumberNewInstanceStrategy();

  /** Default ViewNumber starts with -1. */
  public ViewNumber() {
    this(-1);
  }

  public static ViewNumber increaseViewNumber(ViewNumber current) {
    return ViewNumber.of(current.value() + 1);
  }

  @Override
  public int compareTo(ViewNumber other) {
    return Integer.compare(this.value, other.value);
  }

  @Override
  public String toString() {
    return "ViewNum(" + this.value + ")";
  }

  public static ViewNumber defaultViewNumber() {
    return ViewNumber.of(-1);
  }

  public static ViewNumber of(int viewNumber) {
    return instanceStrategy.getInstance(viewNumber);
  }

  public static void setInstanceStrategy(ViewNumberStrategy newStrategy) {
    instanceStrategy = newStrategy;
  }

  private interface ViewNumberStrategy {
    ViewNumber getInstance(int viewNumber);
  }

  public static class ViewNumberNewInstanceStrategy implements ViewNumberStrategy {
    @Override
    public ViewNumber getInstance(int viewNumber) {
      return new ViewNumber(viewNumber);
    }
  }

  public static class ViewNumberCacheMapStrategy implements ViewNumberStrategy {
    private final ConcurrentMap<Integer, ViewNumber> viewNumberCache = new ConcurrentHashMap<>();

    @Override
    public ViewNumber getInstance(int viewNumber) {
      return viewNumberCache.computeIfAbsent(viewNumber, x -> new ViewNumber(viewNumber));
    }
  }
}
