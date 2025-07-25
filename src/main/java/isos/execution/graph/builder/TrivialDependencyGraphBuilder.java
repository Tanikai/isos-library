package isos.execution.graph.builder;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;

import java.util.Set;

public class TrivialDependencyGraphBuilder implements DependencyGraphBuilder {
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    return null;
  }

  @Override
  public DependencyGraph buildDependencyGraphExp(SequenceNumber v, Set<SequenceNumber> executionWindow) {
    return null;
  }
}
