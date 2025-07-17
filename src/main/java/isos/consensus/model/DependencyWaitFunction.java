package isos.consensus.model;

import java.util.Set;

/**
 * Pseudocode line 60-65
 */
@FunctionalInterface
public interface DependencyWaitFunction {
  /**
   *
   * @param d The dependencies that the caller should wait for.
   * @throws InterruptedException If the waiting thread gets interrupted / canceled.
   */
  void waitUntilConsensusStarted(Set<SequenceNumber> d) throws InterruptedException;
}
