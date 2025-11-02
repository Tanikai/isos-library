package isos.execution.scc;

public enum SccStrategy {
  SEQUENTIAL_TARJAN,
  CONCURRENT_TARJAN;

  public static SccStrategy parse(String s) throws IllegalArgumentException {
    s = s.toLowerCase();
    if (s.equals("sequentialtarjan")) {
      return SEQUENTIAL_TARJAN;
    } else if (s.equals("concurrenttarjan")) {
      return CONCURRENT_TARJAN;
    } else {
      throw new IllegalArgumentException(s + " is not a valid SccStrategy");
    }
  }
}
