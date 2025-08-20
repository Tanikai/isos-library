package isos.execution.graph;

import isos.consensus.model.SequenceNumber;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyGraphTarjanTest {
  // Empty test class for TarjanStronglyConnectedComponents

  public static List<SequenceNumber> generateSequenceNumbers(ReplicaId replicaId, int count) {
    List<SequenceNumber> result = new LinkedList<>();
    for (int i = 1; i <= count; i++) {
      result.add(new SequenceNumber(replicaId, i));
    }
    return result;
  }

  public static List<SequenceNumber> sequenceNumbersOf(int replicaIdInt, List<Integer> counters) {
    return counters.stream().map(counter -> new SequenceNumber(replicaIdInt, counter)).toList();
  }

  public static Dependency createEdge(int replicaIdInt, int from, int to) {
    var replicaId = new ReplicaId(replicaIdInt);
    return new Dependency(new SequenceNumber(replicaId, from), new SequenceNumber(replicaId, to));
  }

  /**
   * Test data from https://en.wikipedia.org/wiki/File:Tarjan%27s_Algorithm_Animation.gif Node
   * order:
   *
   * <p>1 2 3 4
   *
   * <p>5 6 7 8
   */
  @Test
  void testTarjansSCCAlgorithm() {
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

    DependencyGraph depGraph = new DependencyGraph(nodes, edges);
    var SCCs = DependencyGraph.TarjanStronglyConnectedComponents(depGraph);

    assertEquals(4, SCCs.size());
    assertEquals(Set.copyOf(sequenceNumbersOf(0, List.of(1, 2, 5))), Set.copyOf(SCCs.get(0)));
    assertEquals(Set.copyOf(sequenceNumbersOf(0, List.of(3, 4))), Set.copyOf(SCCs.get(1)));
    assertEquals(Set.copyOf(sequenceNumbersOf(0, List.of(6, 7))), Set.copyOf(SCCs.get(2)));
    assertEquals(Set.copyOf(sequenceNumbersOf(0, List.of(8))), Set.copyOf(SCCs.get(3)));
  }
}
