package isos.consensus.dependency;

public enum CompactDepSetStrategy {
  TRIVIAL,
  HIGHEST_PER_REPLICA;

  public static CompactDepSetStrategy parse(String s) throws IllegalArgumentException {
    s = s.toLowerCase();
    if (s.equals("trivial")) {
      return TRIVIAL;
    } else if (s.equals("highestperreplica")) {
      return HIGHEST_PER_REPLICA;
    } else {
      throw new IllegalArgumentException(s + " is not a valid CompactDepSetStrategy");
    }
  }
}
