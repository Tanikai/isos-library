package isos.execution.graph.builder;

import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.optimizations.DepGraphExecutionStrategy;

public class DepGraphBuilderFactory {
  public static DependencyGraphBuilder createDependencyGraphBuilder(
      DepGraphExecutionStrategy strategy, int executionWindowSize) {
    return switch (strategy) {
      case TRIVIAL -> new TrivialDependencyGraphBuilder(executionWindowSize);
      case CACHED -> null;
    };
  }
}
