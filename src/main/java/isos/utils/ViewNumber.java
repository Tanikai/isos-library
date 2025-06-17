package isos.utils;

public record ViewNumber(int value) {

  /**
   * Default ViewNumber starts with -1.
   */
  public ViewNumber() {
    this(-1);
  }
}
