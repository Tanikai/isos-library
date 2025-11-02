package isos.execution.graph.optimizations;

public enum DepGraphExecutionStrategy {
  TRIVIAL,
  CACHED;

  public static DepGraphExecutionStrategy parse(String s) throws IllegalArgumentException {
    s = s.toLowerCase();
    if (s.equals("trivial")) {
      return TRIVIAL;
    } else if (s.equals("cached")) {
      return CACHED;
    } else {
      throw new IllegalArgumentException(s + " is not a valid DepGraphExecutionStrategy");
    }
  }
}
