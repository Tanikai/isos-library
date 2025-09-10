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

  public static List<Set<SequenceNumber>> TarjanSCC(
      Map<SequenceNumber, Set<SequenceNumber>> adjList, Set<SequenceNumber> vertices) {
    final int[] indexCounter = {
      0
    }; // has to be an array because we are mutating an outside variable from the lambda
    Deque<SequenceNumber> stack = new ArrayDeque<>();
    Set<SequenceNumber> stackLookup = new HashSet<>();
    Map<SequenceNumber, Integer> lowlinks = new HashMap<>();
    Map<SequenceNumber, Integer> index = new HashMap<>();
    List<Set<SequenceNumber>> result = new ArrayList<>();

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
            for (SequenceNumber successor : adjList.getOrDefault(node, Set.of())) {
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
              Set<SequenceNumber> connectedComponent = new HashSet<>();
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

    for (SequenceNumber node : vertices) {
      if (!lowlinks.containsKey(node)) {
        strongConnect.accept(node);
      }
    }
    return result;
  }

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

  /**
   * Builds a DAG with super-vertices
   *
   * @param graph
   * @param sccLookup
   * @param SCCs
   * @return
   */
  public static Map<Integer, Set<Integer>> buildSccDAG(
      Map<SequenceNumber, Set<SequenceNumber>> graph,
      Map<SequenceNumber, Integer> sccLookup,
      List<Set<SequenceNumber>> SCCs) {

    // DAG = (V, E) with edge list
    Map<Integer, Set<Integer>> dag = new HashMap<>();
    for (int i = 0; i < SCCs.size(); i++) {
      dag.put(i, new HashSet<>());
    }

    for (SequenceNumber from : graph.keySet()) {
      int sccFromId = sccLookup.get(from);
      for (SequenceNumber to : graph.get(from)) {
        int sccToId = sccLookup.get(to);
        if (sccFromId != sccToId) {
          dag.get(sccFromId).add(sccToId);
        }
      }
    }

    return dag;
  }

  /**
   * Builds a SequenceNumber -> SCC ID (Integer) mapping.
   *
   * @param SCCs
   * @return
   */
  public static Map<SequenceNumber, Integer> buildSccLookup(List<Set<SequenceNumber>> SCCs) {
    Map<SequenceNumber, Integer> sccLookup = new HashMap<>();
    for (int i = 0; i < SCCs.size(); i++) {
      var scc = SCCs.get(i);
      for (var seqNum : scc) {
        sccLookup.put(seqNum, i);
      }
    }
    return sccLookup;
  }

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
  public static List<Set<SequenceNumber>> TarjanSCCDepGraph(DependencyGraph depGraph) {
    Map<SequenceNumber, Set<SequenceNumber>> adjList = new HashMap<>();
    for (var node : depGraph.slots()) {
      adjList.put(node, new HashSet<>());
    }
    for (Dependency edge : depGraph.edges()) {
      adjList.computeIfAbsent(edge.from(), ifAbsent -> new HashSet<>()).add(edge.to());
    }

    return DependencyGraph.TarjanSCC(adjList, depGraph.slots);
  }
}
