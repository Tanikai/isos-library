package isos.utils;

public class QuorumUtil {

  /**
   * Calculates the minimum number of nodes required to form a quorum.
   *
   * @param n total number of nodes
   * @param f maximum number of faulty nodes tolerated
   * @param isBFT Byzantine fault tolerant or not
   * @return minimum size for quorum
   */
  public static int getReplyQuorum(int n, int f, boolean isBFT) throws IllegalArgumentException {
    if (f < 0 || n < 0) {
      throw new IllegalArgumentException("n and f both have to be positive");
    }

    if (isBFT) {
      // Note that a quorum ensures Linearizability when unordered requests are used
      // f + 1 is sufficient in case the replicas' system configuration does not support unordered
      // requests
      return ((n + f) / 2 + 1);
    } else {
      return (n / 2 + 1);
    }
  }
}
