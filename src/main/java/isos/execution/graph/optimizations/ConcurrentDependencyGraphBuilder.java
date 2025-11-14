package isos.execution.graph.optimizations;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.ExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ConcurrentDependencyGraphBuilder implements DependencyGraphBuilder {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConcurrentMap<SequenceNumber, Set<SequenceNumber>> committedWithDepsMap;
  private final ConcurrentMap<SequenceNumber, Boolean> executedSet;
  private final int expansionLimitSize;

  private final ExecutorService executor;

  private final int numWorkers;

  public ConcurrentDependencyGraphBuilder(int expansionLimitSize, int numWorkers) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
    this.numWorkers = numWorkers;
    this.executor = Executors.newFixedThreadPool(numWorkers);
    this.expansionLimitSize = expansionLimitSize;
  }

  @Override
  public void addCommittedWithDeps(SequenceNumber seqNum, Set<SequenceNumber> deps) {
    this.committedWithDepsMap.put(seqNum, deps);
  }

  @Override
  public void addExecuted(SequenceNumber executedSlot) {
    this.executedSet.put(executedSlot, true);
  }

  static class DependencyIterationPhaser extends Phaser {
    BlockingQueue<SequenceNumber> remainingTasks;

    public DependencyIterationPhaser(BlockingQueue<SequenceNumber> remainingTasks) {
      super();
      this.remainingTasks = remainingTasks;
    }

    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
      // End if we have no registered parties or we have no remaining tasks left
      return registeredParties == 0 || remainingTasks.isEmpty();
    }
  }

  /**
   * Handles both the normal dependency graph and dependency graph with expansion limit filtering.
   * For the normal dependency graph, pass null for the executionWindowSlots.
   *
   * @param v
   * @param executionWindowSlots
   * @return
   */
  private DependencyGraph buildDependencyGraphConcurrently(
      SequenceNumber v, Set<SequenceNumber> executionWindowSlots) {
    if (this.executedSet.containsKey(v)) {
      logger.error("SequenceNumber {} is already executed", v);
      return new DependencyGraph(Set.of(), Set.of(), false);
    }

    Set<SequenceNumber> depGraphNodes = ConcurrentHashMap.newKeySet();
    BlockingQueue<SequenceNumber> unexecutedUnexplored = new LinkedBlockingQueue<>();
    // Mark initial node as visited and pending = 1
    ConcurrentHashMap<SequenceNumber, Boolean> visited = new ConcurrentHashMap<>();
    visited.put(v, Boolean.TRUE);
    unexecutedUnexplored.add(v);

    Set<Dependency> edges = ConcurrentHashMap.newKeySet();

    // Stop flags
    AtomicBoolean uncommittedRequestFound = new AtomicBoolean(false);

    DependencyIterationPhaser processingDone = new DependencyIterationPhaser(unexecutedUnexplored);
    CountDownLatch finished = new CountDownLatch(numWorkers);

    Runnable processNode =
        () -> {
          processingDone.register();
          while (!uncommittedRequestFound.get() && !processingDone.isTerminated()) {
            try {
              SequenceNumber current = unexecutedUnexplored.poll();
              if (current == null) {
                // we did not get a new request
                continue;
              }

              // Process the current node
              if (depGraphNodes.contains(current)) {
                // already explored (cycle)
                continue;
              }
              depGraphNodes.add(current);

              var slotDeps = this.committedWithDepsMap.get(current);
              if (slotDeps == null) {
                // Uncommitted dependency -> abort early and return dependency graph
                uncommittedRequestFound.set(true);
                continue;
              }

              var unexecutedDeps =
                  slotDeps.stream()
                      .filter(
                          el ->
                              !this.executedSet.containsKey(el)
                                  && (executionWindowSlots == null
                                      || executionWindowSlots.contains(el)))
                      .toList();

              edges.addAll(
                  unexecutedDeps.stream()
                      .map(dep -> new Dependency(current, dep))
                      .collect(Collectors.toSet()));

              // Enqueue newly discovered deps, mark visited at enqueue time
              List<SequenceNumber> toAdd = new ArrayList<>(unexecutedDeps.size());
              for (SequenceNumber dep : unexecutedDeps) {
                if (!committedWithDepsMap.containsKey(dep)) {
                  uncommittedRequestFound.set(true);
                  continue;
                }
                // If it was not yet visited, add the dependency to the visited
                if (visited.putIfAbsent(dep, Boolean.TRUE) == null) {
                  toAdd.add(dep);
                }
              }
              if (!toAdd.isEmpty()) {
                unexecutedUnexplored.addAll(toAdd);
              }
            } finally {
              // At the end of each iteration, wait for other workers to finish processing
              processingDone.arriveAndAwaitAdvance();
            }
          }

          processingDone.arriveAndDeregister();
          // Signal completion to main thread
          finished.countDown();
        };

    for (int i = 0; i < numWorkers; i++) {
      executor.submit(processNode);
    }

    // Wait until all workers signalled completion
    try {
      finished.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return new DependencyGraph(depGraphNodes, edges, !uncommittedRequestFound.get());
  }

  /**
   * Returns the dependency graph for a given sequence number.
   *
   * @param v
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    return this.buildDependencyGraphConcurrently(v, null);
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

    return this.buildDependencyGraphConcurrently(v, executionWindowSlots);
  }
}
