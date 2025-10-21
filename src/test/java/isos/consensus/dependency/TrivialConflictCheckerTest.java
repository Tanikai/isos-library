package isos.consensus.dependency;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.execution.scc.SccUtils;
import isos.execution.scc.TarjanSCC;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import javax.swing.plaf.synth.SynthColorChooserUI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TrivialConflictCheckerTest {
  public static List<SequenceNumber> generateSequenceNumbers(ReplicaId replicaId, int count) {
    List<SequenceNumber> result = new LinkedList<>();
    for (int i = 1; i <= count; i++) {
      result.add(SequenceNumber.of(replicaId, i));
    }
    return result;
  }

  public static Dependency createEdge(int replicaIdInt, int from, int to) {
    var replicaId = ReplicaId.of(replicaIdInt);
    return new Dependency(SequenceNumber.of(replicaId, from), SequenceNumber.of(replicaId, to));
  }

  public static SequenceNumber getSeqNum(int replicaIdInt, int nodeId) {
    return SequenceNumber.of(replicaIdInt, nodeId);
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
    Set<SequenceNumber> nodes = new HashSet<>(generateSequenceNumbers(ReplicaId.of(0), 8));
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
    var sccFinder = new TarjanSCC();
    Map<SequenceNumber, Set<SequenceNumber>> graph = new HashMap<>();

    SequenceNumber v0 = SequenceNumber.of(0, 0); // Client 1, K1
    SequenceNumber v1 = SequenceNumber.of(0, 1); // Client 1, K2
    SequenceNumber v2 = SequenceNumber.of(0, 2); // Client 1, K1
    SequenceNumber v3 = SequenceNumber.of(0, 3); // Client 2, K2
    SequenceNumber v4 = SequenceNumber.of(0, 4); // Client 2, K1
    SequenceNumber v5 = SequenceNumber.of(0, 5); // Client 2, K2

    graph.put(v0, Set.of());
    graph.put(v1, Set.of(v0));
    graph.put(v2, Set.of(v1));
    graph.put(v3, Set.of(v1));
    graph.put(v4, Set.of(v2, v3)); // accesses K1, and is from Client 2 -> both required

    // Node 5 is new command, and accesses K2 -> all K2 commands, and all Client 2 commands in
    // complete set
    Set<SequenceNumber> completeDependencySet = Set.of(v1, v3, v4);
    Set<SequenceNumber> expectedCompactDepSet =
        Set.of(v4); // v4 (the latest Client 2 command) is the only direct dependency

    Set<SequenceNumber> actualCompactDepSet =
        TrivialConflictChecker.removeRedundantDependencies(sccFinder, graph, v5, completeDependencySet);

    assertEquals(expectedCompactDepSet, actualCompactDepSet);
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
    var sccFinder = new TarjanSCC();
    var adjList = DependencyGraph.toAdjacencyList(depGraph);
    List<Set<SequenceNumber>> SCCs = sccFinder.getSCC(adjList, depGraph.slots());
    Map<SequenceNumber, Integer> sccLookup = SccUtils.buildSccLookup(SCCs);
    Map<Integer, Set<Integer>> sccDag = SccUtils.buildSccDAG(adjList, sccLookup, SCCs);

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
