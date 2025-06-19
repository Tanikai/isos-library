package isos.utils;

import java.io.Serializable;

public record ViewNumber(int value) implements Serializable {

  /** Default ViewNumber starts with -1. */
  public ViewNumber() {
    this(-1);
  }
}
