package isos.execution.graph.optimizations;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OptimizedDependencyGraphBuilder implements DependencyGraphBuilder {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConcurrentMap<SequenceNumber, Set<SequenceNumber>> committedWithDepsMap;
  private final ConcurrentMap<SequenceNumber, Boolean> executedSet;
  private final int executionWindowSize;

  public OptimizedDependencyGraphBuilder(int executionWindowSize) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
    this.executionWindowSize = executionWindowSize;
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
   * Returns the dependency graph for a given sequence number.
   * @param v
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    return null;
  }

  @Override
  public Set<SequenceNumber> getExpansionLimitSlots() {
    return Set.of();
  }

  /**
   *
   * @param v
   * @param executionWindowSlots
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraphExp(SequenceNumber v, Set<SequenceNumber> executionWindowSlots) {
    return null;
  }
}
