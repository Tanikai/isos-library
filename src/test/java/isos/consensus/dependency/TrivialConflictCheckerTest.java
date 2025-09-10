package isos.consensus.dependency;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrivialConflictCheckerTest {
  public static List<SequenceNumber> generateSequenceNumbers(ReplicaId replicaId, int count) {
    List<SequenceNumber> result = new LinkedList<>();
    for (int i = 1; i <= count; i++) {
      result.add(new SequenceNumber(replicaId, i));
    }
    return result;
  }

  public static Dependency createEdge(int replicaIdInt, int from, int to) {
    var replicaId = new ReplicaId(replicaIdInt);
    return new Dependency(new SequenceNumber(replicaId, from), new SequenceNumber(replicaId, to));
  }

  public static SequenceNumber getSeqNum(int replicaIdInt, int nodeId) {
    return new SequenceNumber(replicaIdInt, nodeId);
  }

  /**
   *
   *
   * <pre>
   *   +---+       +---+       +---+ <---- +---+
   *   | 1 | <---- | 2 | <---- | 3 |       | 4 |
   *   +---+       +---+       +---+ ----> +---+
   *     |     ^    ^            ^          ^
   *     v   /      |            |          |
   *   +---+       +---+ <---- +---+       +---+ <-+
   *   | 5 | <---- | 6 |       | 7 | <---- | 8 |   |
   *   +---+       +---+ ----> +---+       +---+ --+
   * </pre>
   */
  DependencyGraph getTestGraph() {
    Set<SequenceNumber> nodes = new HashSet<>(generateSequenceNumbers(new ReplicaId(0), 8));
    Set<Dependency> edges = new HashSet<>();
    edges.add(createEdge(0, 1, 5));
    edges.add(createEdge(0, 2, 1));
    edges.add(createEdge(0, 3, 2));
    edges.add(createEdge(0, 3, 4));
    edges.add(createEdge(0, 4, 3));
    edges.add(createEdge(0, 5, 2));
    edges.add(createEdge(0, 6, 2));
    edges.add(createEdge(0, 6, 5));
    edges.add(createEdge(0, 6, 7));
    edges.add(createEdge(0, 7, 3));
    edges.add(createEdge(0, 7, 6));
    edges.add(createEdge(0, 8, 4));
    edges.add(createEdge(0, 8, 7));
    edges.add(createEdge(0, 8, 8));

    return new DependencyGraph(nodes, edges);
  }

  SequenceNumber getSeqNum(int nodeId) {
    return getSeqNum(0, nodeId);
  }

  // TODO Test: Is result from TarjanSCCDepGraph and TarjanSCC same?

  @Test
  void TestRemoveRedundantDependencies() {
    Map<SequenceNumber, Set<SequenceNumber>> graph = new HashMap<>();
    SequenceNumber newVertex = new SequenceNumber(1, 3);

    //    TrivialConflictChecker.removeRedundantDependencies();
  }

  /**
   *
   *
   * <pre>
   *   +---+       +---+       +---+ <---- +---+
   *   | 1 | <---- | 2 | <---- | 3 |       | 4 |
   *   +---+       +---+       +---+ ----> +---+
   *     |     ^    ^            ^          ^
   *     v   /      |            |          |
   *   +---+       +---+ <---- +---+       +---+ <-+
   *   | 5 | <---- | 6 |       | 7 | <---- | 8 |   |
   *   +---+       +---+ ----> +---+       +---+ --+
   * </pre>
   */
  @Test
  void TestIsSccReachableWithout() {
    // Setup
    var depGraph = getTestGraph();
    List<Set<SequenceNumber>> SCCs = DependencyGraph.TarjanSCCDepGraph(depGraph);
    Map<SequenceNumber, Integer> sccLookup = DependencyGraph.buildSccLookup(SCCs);
    var adjList = DependencyGraph.toAdjacencyList(depGraph);
    Map<Integer, Set<Integer>> sccDag = DependencyGraph.buildSccDAG(adjList, sccLookup, SCCs);

    var node1Scc = sccLookup.get(getSeqNum(1));
    var node2Scc = sccLookup.get(getSeqNum(2));
    var node3Scc = sccLookup.get(getSeqNum(3));
    var node5Scc = sccLookup.get(getSeqNum(5));
    var node6Scc = sccLookup.get(getSeqNum(6));

    // Case 1.1: directed edge within a SCC -> not reachable
    assertFalse(TrivialConflictChecker.isSccReachableWithout(sccDag, node1Scc, node5Scc));
    // Case 1.2: both in SCC, but reverse edge 2->1
    assertFalse(TrivialConflictChecker.isSccReachableWithout(sccDag, node1Scc, node2Scc));
    // Case 2: single directed edge from one SCC to other SCC, but without alternative path
    assertFalse(TrivialConflictChecker.isSccReachableWithout(sccDag, node3Scc, node2Scc));
    // Case 3: SCC to other SCC, but with alternative path
    assertTrue(TrivialConflictChecker.isSccReachableWithout(sccDag, node6Scc, node2Scc));
  }
}
