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
}
