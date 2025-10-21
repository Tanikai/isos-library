package isos.execution.scc;

import isos.consensus.model.SequenceNumber;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SccFinder {
  /**
   * Returns the strongly connected components of a given graph in reverse topological order.
   *
   * @return
   */
  List<Set<SequenceNumber>> getSCC(
      Map<SequenceNumber, Set<SequenceNumber>> adjList, Set<SequenceNumber> vertices);
}
