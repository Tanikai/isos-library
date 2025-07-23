package isos.utils;

import java.io.Serializable;

public record ViewNumber(int value) implements Serializable, Comparable<ViewNumber> {

  /** Default ViewNumber starts with -1. */
  public ViewNumber() {
    this(-1);
  }

  public static ViewNumber increaseViewNumber(ViewNumber current) {
    return new ViewNumber(current.value() + 1);
  }

  @Override
  public int compareTo(ViewNumber other) {
    return Integer.compare(this.value, other.value);
  }
}
