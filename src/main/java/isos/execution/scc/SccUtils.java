package isos.execution.scc;

import isos.consensus.model.SequenceNumber;

import java.util.*;

public class SccUtils {
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

    // DAG = (V, E) with adjacency list
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
   * Determines the topological order of SCCs based on Kahn's algorithm, while grouping them up into
   * levels where the nodes can be executed concurrently. For ISOS, we need a reverse topological
   * order, so the result is reversed before returning it.
   *
   * @param sccDAG
   * @return List of levels / group, where each concurrency group contains SCC IDs that can be
   *     executed concurrently. The groups have to be executed sequentially, starting from the first
   *     element in the list.
   */
  public static List<Set<Integer>> getSccConcurrencyGroups(Map<Integer, Set<Integer>> sccDAG) {
    // inDegree <SCC ID, number of incoming edges>
    int[] inDegree = new int[sccDAG.size()];

    for (Set<Integer> deps : sccDAG.values()) {
      for (var d : deps) {
        inDegree[d]++;
      }
    }

    List<Set<Integer>> levels = new ArrayList<>();
    Set<Integer> currentLevel = new HashSet<>();

    // Set of all nodes with no incoming edge -> executed last, starting point of graph traversal
    for (int i = 0; i < inDegree.length; i++) {
      if (inDegree[i] == 0) {
        currentLevel.add(i);
      }
    }

    while (!currentLevel.isEmpty()) {
      levels.add(new HashSet<>(currentLevel));
      Set<Integer> nextLevel = new HashSet<>();

      for (int scc : currentLevel) {
        for (int dependent : sccDAG.get(scc)) {
          // remove edge from graph
          inDegree[dependent]--;
          // if no other incoming edges, then add to level
          if (inDegree[dependent] == 0) {
            nextLevel.add(dependent);
          }
        }
      }

      currentLevel = nextLevel;
    }

    return levels.reversed();
  }
}
