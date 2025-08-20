package isos.execution.graph;

import isos.consensus.model.SequenceNumber;

import java.util.*;
import java.util.function.Consumer;

/**
 * The dependency graph is a simple graph G = (V, E) data structure.
 *
 * @param slots
 * @param edges
 */
public record DependencyGraph(Set<SequenceNumber> slots, Set<Dependency> edges) {

  /**
   * Tarjan's Algorithm to determine strongly connected components of a graph.
   *
   * <p>https://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm -> "No
   * strongly connected component will be identified before any of its successors [...] constitutes
   * a reverse topological sort of the DAG formed by the strongly connected components" i.e. the
   * SCCs form a DAG, even though the original graph might not be a DAG
   *
   * <p>Implementation based on: https://www.logarithmic.net/pfh/blog/01208083168,
   * https://logarithmic.net/pfh-files/blog/01208083168/tarjan.py
   *
   * @param depGraph
   * @return List of SCCs (represented as List of SequenceNumbers)
   */
  public static List<List<SequenceNumber>> TarjanStronglyConnectedComponents(
      DependencyGraph depGraph) {

    Map<SequenceNumber, List<SequenceNumber>> adjList = new HashMap<>();
    for (var node : depGraph.slots()) {
      adjList.put(node, new ArrayList<>());
    }
    for (Dependency edge : depGraph.edges()) {
      adjList.computeIfAbsent(edge.from(), ifAbsent -> new ArrayList<>()).add(edge.to());
    }

    final int[] indexCounter = {
      0
    }; // has to be an array because we are mutating an outside variable from the lambda
    Deque<SequenceNumber> stack = new ArrayDeque<>();
    Set<SequenceNumber> stackLookup = new HashSet<>();
    Map<SequenceNumber, Integer> lowlinks = new HashMap<>();
    Map<SequenceNumber, Integer> index = new HashMap<>();
    List<List<SequenceNumber>> result = new LinkedList<>();

    // We need to create a Consumer because of recursion -> self reference required
    Consumer<SequenceNumber> strongConnect =
        new Consumer<>() {
          @Override
          public void accept(SequenceNumber node) {
            // Set the depth index for this node to the smallest unused index
            index.put(node, indexCounter[0]);
            lowlinks.put(node, indexCounter[0]);
            indexCounter[0] += 1;
            stack.push(node);
            stackLookup.add(node);

            // Consider successors of "node"
            for (SequenceNumber successor : adjList.getOrDefault(node, List.of())) {
              if (!lowlinks.containsKey(successor)) {
                // Successor has not yet been visited; recurse on it
                this.accept(successor);
                lowlinks.put(node, Math.min(lowlinks.get(node), lowlinks.get(successor)));
              } else if (stackLookup.contains(successor)) {
                // The successor is in the stack and hence in the current SCC
                lowlinks.put(node, Math.min(lowlinks.get(node), index.get(successor)));
              }
            }

            // If "node" is a root node, pop the stack and generate an SCC
            if (lowlinks.get(node).equals(index.get(node))) {
              List<SequenceNumber> connectedComponent = new LinkedList<>();
              SequenceNumber successor;
              do {
                successor = stack.pop();
                stackLookup.remove(successor); // helper
                connectedComponent.add(successor);
              } while (!successor.equals(node));
              result.add(connectedComponent);
            }
          }
        };

    for (SequenceNumber node : depGraph.slots()) {
      if (!lowlinks.containsKey(node)) {
        strongConnect.accept(node);
      }
    }

    return result;
  }
}
