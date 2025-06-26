package isos.graph;

import isos.consensus.SequenceNumber;
import java.util.Set;

/**
 * Pseudocode line 60-65
 */
public interface DependencyWaitFunction {
  void waitUntilConsensusStarted(Set<SequenceNumber> d) throws InterruptedException;
}
