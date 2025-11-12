package isos.execution.graph.builder;

import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.optimizations.CachedDependencyGraphBuilder;
import isos.execution.graph.optimizations.ConcurrentDependencyGraphBuilder;
import isos.execution.graph.optimizations.DepGraphExecutionStrategy;

public class DepGraphBuilderFactory {
  public static DependencyGraphBuilder createDependencyGraphBuilder(
      DepGraphExecutionStrategy strategy, int expansionLimitSize) {
    return switch (strategy) {
      case TRIVIAL -> new TrivialDependencyGraphBuilder(expansionLimitSize);
      case CACHED -> new CachedDependencyGraphBuilder(expansionLimitSize);
      case CONCURRENT -> new ConcurrentDependencyGraphBuilder(expansionLimitSize, 4);
    };
  }
}
