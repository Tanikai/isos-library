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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ConcurrentDependencyGraphBuilder implements DependencyGraphBuilder {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConcurrentMap<SequenceNumber, Set<SequenceNumber>> committedWithDepsMap;
  private final ConcurrentMap<SequenceNumber, Boolean> executedSet;
  private final int expansionLimitSize;

  private final ExecutorService executor;

  public ConcurrentDependencyGraphBuilder(int expansionLimitSize) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
    this.executor = Executors.newFixedThreadPool(2);
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
      return new DependencyGraph(Set.of(), Set.of());
    }

    int numWorkers = 2;

    Set<SequenceNumber> depGraphNodes = ConcurrentHashMap.newKeySet();
    BlockingQueue<SequenceNumber> unexecutedUnexplored = new LinkedBlockingQueue<>();
    // Mark initial node as visited and pending = 1
    ConcurrentHashMap<SequenceNumber, Boolean> visited = new ConcurrentHashMap<>();
    visited.put(v, Boolean.TRUE);
    unexecutedUnexplored.add(v);

    Set<Dependency> edges = ConcurrentHashMap.newKeySet();

    // Stop flags
    AtomicBoolean uncommittedRequestFound = new AtomicBoolean(false);
    AtomicBoolean noMoreWork = new AtomicBoolean(false);

    // await/signal termination
    ReentrantLock waitingLock = new ReentrantLock();
    Condition waitingForNewRequest = waitingLock.newCondition();
    Condition allDone = waitingLock.newCondition();
    AtomicInteger waitingWorkers = new AtomicInteger(0);
    AtomicInteger finishedTasks = new AtomicInteger(0);

    Runnable processNode =
        () -> {
          try {
            while (!uncommittedRequestFound.get() && !noMoreWork.get()) {
              SequenceNumber current = unexecutedUnexplored.poll();

              if (current == null) {
                // Currently no available task
                int totalWaiting = waitingWorkers.incrementAndGet();
                try {
                  if (totalWaiting == numWorkers) {
                    logger.info("All workers are waiting, stop depGraph");
                    // All workers waiting and no queued work -> request termination
                    noMoreWork.set(true);
                    waitingLock.lock();
                    logger.info("Acquired all waiting worker lock, stop depGraph");
                    try {
                      waitingForNewRequest.signalAll();
                    } finally {
                      waitingLock.unlock();
                    }
                    break;
                  }

                  try {
                    // Only some workers waiting -> we will wait for new work
                    logger.info("Some workers are waiting, self wait");
                    waitingLock.lock();
                    try {
                      // Before awaiting, we have to check the stop conditions again -> we could
                      // be woken up to stop
                      if (uncommittedRequestFound.get() || noMoreWork.get()) {
                        logger.info("Woken up and saw that all requests done");
                        break;
                      }
                      logger.info("Acquired some workers waiting, self wait");
                      waitingForNewRequest.await();
                    } finally {
                      waitingLock.unlock();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                  }
                } finally {
                  waitingWorkers.decrementAndGet();
                }
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
                break;
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
                // If it was not yet visited, add the dependency to the visited
                if (visited.putIfAbsent(dep, Boolean.TRUE) == null) {
                  toAdd.add(dep);
                }
              }
              if (!toAdd.isEmpty()) {
                unexecutedUnexplored.addAll(toAdd);

                // If there are waiting workers, we can wake them up
                if (waitingWorkers.get() > 0) {
                  waitingLock.lock();
                  waitingForNewRequest.signalAll();
                  waitingLock.unlock();
                }
              }
            }
          } finally {
            logger.info("Runner finished");
            int finished = finishedTasks.incrementAndGet();
            if (finished == numWorkers) {
              logger.info("All runners finished, try acquire lock");
              waitingLock.lock();
              logger.info("Acquired lock to signal main thread");
              try {
                allDone.signalAll();
              } finally {
                waitingLock.unlock();
              }
            }
          }
        };

    for (int i = 0; i < numWorkers; i++) {
      executor.submit(processNode);
    }

    // Wait until all workers signalled completion
    logger.info("Main: Try awaiting allDone");
    waitingLock.lock();
    try {
      logger.info("Main: Acquired await allDone");
      if (!uncommittedRequestFound.get()  && !noMoreWork.get()) {
        logger.info("Main: Acquired awaiting allDone, wait for all threads to finish");
        allDone.await();
      } else {
        logger.info("Main: Acquired await allDone, but already done");
      }
      logger.info("Main: All workers finished, return dependency graph");
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for DependencyGraph");
      Thread.currentThread().interrupt();
    } finally {
      waitingLock.unlock();
    }

    return new DependencyGraph(depGraphNodes, edges);
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
    // Pseudocode line 166
    if (!executionWindowSlots.contains(v)) {
      return new DependencyGraph(Set.of(), Set.of());
    }

    return this.buildDependencyGraphConcurrently(v, executionWindowSlots);
  }
}
