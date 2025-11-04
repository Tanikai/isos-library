package isos.execution.graph.optimizations;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;

import java.util.Set;

public class UncommittedDepGraph {
  private Set<SequenceNumber> depGraphNodes;
  private Set<Dependency> edges;
  private Set<SequenceNumber> uncommittedCommands;

  public UncommittedDepGraph(
      Set<SequenceNumber> depGraphNodes,
      Set<Dependency> edges,
      Set<SequenceNumber> uncommittedCommands) {
    this.depGraphNodes = depGraphNodes;
    this.edges = edges;
    this.uncommittedCommands = uncommittedCommands;
  }

  public Set<SequenceNumber> getDepGraphNodes() {
    return depGraphNodes;
  }

  public Set<Dependency> getEdges() {
    return edges;
  }

  public Set<SequenceNumber> getUncommittedCommands() {
    return uncommittedCommands;
  }
}
