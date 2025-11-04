package isos.execution.graph.optimizations;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.ExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class CachedDependencyGraphBuilder implements DependencyGraphBuilder {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConcurrentMap<SequenceNumber, Set<SequenceNumber>> committedWithDepsMap;
  private final ConcurrentMap<SequenceNumber, Boolean> executedSet;
  private final int expansionLimitSize;

  private final ConcurrentMap<SequenceNumber, UncommittedDepGraph> cachedDepGraph;

  public CachedDependencyGraphBuilder(int expansionLimitSize) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
    this.cachedDepGraph = new ConcurrentHashMap<>();
    this.expansionLimitSize = expansionLimitSize;
  }

  @Override
  public void addCommittedWithDeps(SequenceNumber seqNum, Set<SequenceNumber> deps) {
    this.committedWithDepsMap.put(seqNum, deps);
  }

  @Override
  public void addExecuted(SequenceNumber executedSlot) {
    this.executedSet.put(executedSlot, true);
    this.cachedDepGraph.remove(executedSlot);
  }

  /**
   * Returns the dependency graph for a given sequence number.
   *
   * @param v
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    if (this.executedSet.containsKey(v)) {
      logger.error("SequenceNumber {} is already executed", v);
      return new DependencyGraph(Set.of(), Set.of());
    }

    Set<SequenceNumber> depGraphNodes;
    Set<SequenceNumber> unexecutedUnexplored = new HashSet<>();
    Set<Dependency> edges;
    Set<SequenceNumber> uncommittedCommands;

    // If we have a cached graph, we can clean it up and process it
    if (this.cachedDepGraph.containsKey(v)) {
      var cached = this.cachedDepGraph.get(v);
      depGraphNodes = cached.getDepGraphNodes();
      // Remove nodes that are already executed
      depGraphNodes.removeAll(this.executedSet.keySet());

      // Remove edge if dependency is already executed or the node itself is already executed
      edges = cached.getEdges();
      edges.removeIf(
          (dep) ->
              this.executedSet.containsKey(dep.to()) || this.executedSet.containsKey(dep.from()));

      uncommittedCommands = cached.getUncommittedCommands();
      for (var previouslyUncommitted : uncommittedCommands) {
        if (this.committedWithDepsMap.containsKey(previouslyUncommitted)) {
          unexecutedUnexplored.add(previouslyUncommitted);
          uncommittedCommands.remove(previouslyUncommitted);
        }
      }
      // if all uncommitted commands are *still* uncommitted, unexecutedUnexplored is empty and
      // we save a new, updated UncommittedDepGraph.

    } else {
      // Nothing cached, so start from scratch
      depGraphNodes = new HashSet<>();
      unexecutedUnexplored = new HashSet<>();
      unexecutedUnexplored.add(v);
      edges = new HashSet<>();
      uncommittedCommands = new HashSet<>();
    }

    while (!unexecutedUnexplored.isEmpty()) {
      // Take out 1 element and add to depGraph
      SequenceNumber current = unexecutedUnexplored.iterator().next();
      unexecutedUnexplored.remove(current);
      if (depGraphNodes.contains(current)) {
        // if we have already explored this node (e.g., cycle), skip it
        continue;
      }
      depGraphNodes.add(current);

      // Iterate over all dependencies of node
      var slotDeps = this.committedWithDepsMap.get(current);
      if (slotDeps == null) {
        // if we can't find the dependencies of the slot, it is uncommitted and it can't be executed
        // now. To prevent processing the complete dependency graph again, we can
        uncommittedCommands.add(current);
        continue;
      }
      // Only process unexecuted deps (executed dependencies are irrelevant)
      var unexecutedDeps =
          slotDeps.stream().filter(el -> !this.executedSet.containsKey(el)).toList();

      // Add edges to unexecuted dependencies
      edges.addAll(
          unexecutedDeps.stream()
              .map(dep -> new Dependency(current, dep))
              .collect(Collectors.toSet()));

      // Add all unexecuted dependencies for further iteration
      unexecutedUnexplored.addAll(unexecutedDeps);
    }

    // If we have uncommitted commands, we can cache the dependency graph, and then iterate over
    // the uncommitted commands.
    if (!uncommittedCommands.isEmpty()) {
      var cache = new UncommittedDepGraph(depGraphNodes, edges, uncommittedCommands);
      this.cachedDepGraph.put(v, cache);
    }

    return new DependencyGraph(depGraphNodes, edges);
  }

  @Override
  public Set<SequenceNumber> getExecutionWindow() {
    return ExecutionUtils.executedAndExecutionWindowSlots(
        this.committedWithDepsMap.keySet(), this.executedSet.keySet(), this.expansionLimitSize);
  }

  /**
   * @param v
   * @param executionWindowSlots
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraphExp(
      SequenceNumber v, Set<SequenceNumber> executionWindowSlots) {
    // If v is outside of execution window, do not execute it, because there will be another v
    // that can be executed
    if (!executionWindowSlots.contains(v)) {
      return new DependencyGraph(Set.of(), Set.of());
    }

    return null;
  }
}
