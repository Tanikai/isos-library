package isos.execution.graph;

import isos.consensus.model.SequenceNumber;

import java.util.*;

/**
 * The dependency graph is a simple graph G = (V, E) data structure.
 *
 * @param slots
 * @param edges
 */
public record DependencyGraph(
    Set<SequenceNumber> slots, Set<Dependency> edges, boolean canBeExecuted) {

  public static Map<SequenceNumber, Set<SequenceNumber>> toAdjacencyList(DependencyGraph graph)
      throws MissingSourceVertexException {
    Map<SequenceNumber, Set<SequenceNumber>> result = new HashMap<>();
    for (var slot : graph.slots()) {
      result.put(slot, new HashSet<>());
    }

    for (var edge : graph.edges()) {
      if (!result.containsKey(edge.from())) {
        throw new MissingSourceVertexException(edge);
      }
      result.get(edge.from()).add(edge.to());
    }

    return result;
  }
}
