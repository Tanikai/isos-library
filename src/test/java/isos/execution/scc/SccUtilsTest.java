package isos.execution.scc;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SccUtilsTest {

  @Test
  void testSingleSCC() {
    // Single SCC with no dependencies
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(1, levels.size());
    assertEquals(Set.of(0), levels.get(0));
  }

  @Test
  void testLinearDependencyChain() {
    // 0 -> 1 -> 2 (linear dependency chain)
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, Set.of(1));
    sccDeps.put(1, Set.of(2));
    sccDeps.put(2, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(3, levels.size());
    // 2 has no dependencies, so it has to be executed first
    assertEquals(Set.of(2), levels.get(0));
    assertEquals(Set.of(1), levels.get(1));
    assertEquals(Set.of(0), levels.get(2));
  }

  @Test
  void testParallelSCCs() {
    // Three independent SCCs
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, new HashSet<>());
    sccDeps.put(1, new HashSet<>());
    sccDeps.put(2, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(1, levels.size());
    assertEquals(Set.of(0, 1, 2), levels.get(0));
  }

  @Test
  void testDiamondDependency() {
    // Diamond: 0 -> 1, 2 -> 3 (0 depends on both 1 and 2)
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, Set.of(1, 2));
    sccDeps.put(1, Set.of(3));
    sccDeps.put(2, Set.of(3));
    sccDeps.put(3, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(3, levels.size());
    assertEquals(Set.of(3), levels.get(0));
    assertEquals(Set.of(1, 2), levels.get(1));
    assertEquals(Set.of(0), levels.get(2));
  }

  @Test
  void testComplexDAG() {
    // Complex DAG:
    //          1    3
    //          V    V
    //     0 -> 2 -> 5
    //          V
    //          4
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, Set.of(2));
    sccDeps.put(1, Set.of(2));
    sccDeps.put(2, Set.of(4, 5));
    sccDeps.put(3, Set.of(5));
    sccDeps.put(4, new HashSet<>());
    sccDeps.put(5, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(3, levels.size());
    // 4, 5 executed first
    assertEquals(Set.of(4, 5), levels.get(0));
    // then 2
    assertEquals(Set.of(2), levels.get(1));
    // then 0, 1
    assertEquals(Set.of(0, 1, 3), levels.get(2));

    // as the current implementation creates the topological sort based on levels and *then*
    // reverses it, 3 is contained in the last set, instead of the second one. The vertices without
    // any incoming edges are processed first, which means that {0, 1, 3}
  }

  @Test
  void testMultipleSinksAndSources() {
    // Multiple sources (0, 1) and sinks (3, 4)
    // 0 -> 2 -> 3
    // 1 -> 2 -> 4
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, Set.of(2));
    sccDeps.put(1, Set.of(2));
    sccDeps.put(2, Set.of(3, 4));
    sccDeps.put(3, new HashSet<>());
    sccDeps.put(4, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    assertEquals(3, levels.size());
    assertEquals(Set.of(3, 4), levels.get(0));
    assertEquals(Set.of(2), levels.get(1));
    assertEquals(Set.of(0, 1), levels.get(2));
  }

  @Test
  void testAllSCCsProcessed() {
    // Ensure all SCCs appear exactly once across all levels
    Map<Integer, Set<Integer>> sccDeps = new HashMap<>();
    sccDeps.put(0, Set.of(1, 2));
    sccDeps.put(1, Set.of(3));
    sccDeps.put(2, Set.of(3));
    sccDeps.put(3, new HashSet<>());
    sccDeps.put(4, new HashSet<>());

    List<Set<Integer>> levels = SccUtils.getSccConcurrencyGroups(sccDeps);

    Set<Integer> allProcessed = new HashSet<>();
    for (Set<Integer> level : levels) {
      for (int scc : level) {
        assertFalse(allProcessed.contains(scc), "SCC " + scc + " appears in multiple levels");
        allProcessed.add(scc);
      }
    }

    assertEquals(Set.of(0, 1, 2, 3, 4), allProcessed);
  }

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

  @Test
  void testExampleScc() {
    var sccFinder = new TarjanSCC();
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

    DependencyGraph depGraph = new DependencyGraph(nodes, edges, true);
    var adjList = DependencyGraph.toAdjacencyList(depGraph);
    List<Set<SequenceNumber>> SCCs = sccFinder.getSCC(adjList, depGraph.slots());

    var lookup = SccUtils.buildSccLookup(SCCs);
    System.out.println(lookup);
    var dag = SccUtils.buildSccDAG(adjList, lookup, SCCs);

    var levels = SccUtils.getSccConcurrencyGroups(dag);
    assertEquals(4, levels.size());
    assertEquals(
        Set.of(new SequenceNumber(0, 1), new SequenceNumber(0, 2), new SequenceNumber(0, 5)),
        SCCs.get(0));
    assertEquals(Set.of(0), levels.get(0));
    assertEquals(Set.of(1), levels.get(1));
    assertEquals(Set.of(2), levels.get(2));
    assertEquals(Set.of(3), levels.get(3));
  }
}
