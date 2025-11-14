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
  private final ConcurrentMap<SequenceNumber, UncommittedDepGraph> cachedDepGraphExp;

  public CachedDependencyGraphBuilder(int expansionLimitSize) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
    this.cachedDepGraph = new ConcurrentHashMap<>();
    this.cachedDepGraphExp = new ConcurrentHashMap<>();
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
    this.cachedDepGraphExp.remove(executedSlot);
  }

  /**
   * Handles both the normal dependency graph and dependency graph with expansion limit filtering.
   * For the normal dependency graph, pass null for the executionWindowSlots.
   *
   * @param v
   * @param executionWindowSlots
   * @return
   */
  private DependencyGraph buildDependencyGraphWithCache(
      SequenceNumber v, Set<SequenceNumber> executionWindowSlots) {
    if (this.executedSet.containsKey(v)) {
      logger.error("SequenceNumber {} is already executed", v);
      return new DependencyGraph(Set.of(), Set.of(), false);
    }

    Set<SequenceNumber> depGraphNodes;
    Set<SequenceNumber> unexecutedUnexplored = new HashSet<>();
    Set<Dependency> edges;
    Set<SequenceNumber> uncommittedCommands = new HashSet<>();

    // We need to different data structures for normal dependency graph and dependency graph with
    // expansion limit
    UncommittedDepGraph cached;
    if (executionWindowSlots == null) {
      cached = this.cachedDepGraph.get(v);
    } else {
      cached = this.cachedDepGraphExp.get(v);
    }

    // If we have a cached graph, we can clean it up and process it
    if (cached != null) {
      logger.debug("SeqNum {} has cached dependency graph, reusing cached graph {}", v, cached);
      depGraphNodes = cached.getDepGraphNodes();
      // Remove nodes that are already executed
      depGraphNodes.removeAll(this.executedSet.keySet());

      // Remove edge if dependency is already executed or the node itself is already executed
      edges = cached.getEdges();
      edges.removeIf(
          (dep) ->
              this.executedSet.containsKey(dep.to()) || this.executedSet.containsKey(dep.from()));
      // If we have execution window slots, remove the dependencies that are not in the execution
      // window.
      if (executionWindowSlots != null) {
        edges.removeIf(
            dep ->
                !executionWindowSlots.contains(dep.to())
                    || !executionWindowSlots.contains(dep.from()));
      }

      for (var previouslyUncommitted : cached.getUncommittedCommands()) {
        // if a previously uncommitted command is not inside the execution window, we have to ignore
        // it
        if (executionWindowSlots != null && !executionWindowSlots.contains(previouslyUncommitted)) {
          continue;
        }

        // If a command is now committed, add it to the nodes that we have to explore
        if (this.committedWithDepsMap.containsKey(previouslyUncommitted)) {
          unexecutedUnexplored.add(previouslyUncommitted);
          continue;
        }

        // If it is still uncommitted, add it to the uncommitted map for to cache again later
        uncommittedCommands.add(previouslyUncommitted);
      }
    } else {
      // Nothing cached, so start from scratch
      depGraphNodes = new HashSet<>();
      unexecutedUnexplored = new HashSet<>();
      unexecutedUnexplored.add(v);
      edges = new HashSet<>();
    }

    // if all uncommitted commands are *still* uncommitted, unexecutedUnexplored is empty and
    // we save a new, updated UncommittedDepGraph.
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
        logger.debug("DepGraph({}): dependency {} is uncommitted", v, current);
        continue;
      }

      // Only process unexecuted deps (executed dependencies are irrelevant)
      // In the pseudocode (lines 159 / 171), we add all dependencies of a node to the dependency
      // graph. However, we can optimize this by only including unexecuted dependencies.
      // 1. If a dependency is executed, we can assume that all of its dependencies are executed
      //    as well.
      // 2. If a dependency is part of a SCC and is unexecuted, we proceed as normal and add the
      //    dependency to the unexecutedUnexplored list. As the execution manager only executes
      //    SCCs as a whole, we can assume that all elements of the SCC are unexecuted.
      // 3. If a dependency is part of a SCC and is executed, we can assume that the entire SCC has
      //    been executed as well, as described above. Thus, we do not need to check whether its
      //    dependencies are unexecuted or not.
      // Therefore, is is enough to only explore the dependencies of unexecuted agreement slots.
      var unexecutedDeps =
          slotDeps.stream()
              .filter(
                  el ->
                      // We only add unexecuted dependencies
                      !this.executedSet.containsKey(el)
                          // If we do not have an execution window, always pass true
                          // If we have an execution window (for the unblock case), only add the
                          // dependencies to the graph if they are included in that window
                          && (executionWindowSlots == null || executionWindowSlots.contains(el)))
              .toList();

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
      logger.debug(
          "DepGraph({}): depGraph has uncommitted dependencies {}, cache for reuse later",
          v,
          uncommittedCommands);
      var cache = new UncommittedDepGraph(depGraphNodes, edges, uncommittedCommands);
      if (executionWindowSlots == null) {
        this.cachedDepGraph.put(v, cache);
      } else {
        this.cachedDepGraphExp.put(v, cache);
      }
    }

    return new DependencyGraph(depGraphNodes, edges, uncommittedCommands.isEmpty());
  }

  /**
   * Returns the dependency graph for a given sequence number.
   *
   * @param v
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    return this.buildDependencyGraphWithCache(v, null);
  }

  @Override
  public Set<SequenceNumber> getExecutionWindow() {
    return ExecutionUtils.executedAndExecutionWindowSlots(
        this.committedWithDepsMap.keySet(),
        this.executedSet.keySet(),
        this.expansionLimitSize,
        true);
  }

  @Override
  public Set<SequenceNumber> getExecutionWindowWithoutExecuted() {
    return ExecutionUtils.executedAndExecutionWindowSlots(
        this.committedWithDepsMap.keySet(),
        this.executedSet.keySet(),
        this.expansionLimitSize,
        false);
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
    // Pseudocode line 166
    if (!executionWindowSlots.contains(v)) {
      return new DependencyGraph(Set.of(), Set.of(), false);
    }

    return this.buildDependencyGraphWithCache(v, executionWindowSlots);
  }
}
