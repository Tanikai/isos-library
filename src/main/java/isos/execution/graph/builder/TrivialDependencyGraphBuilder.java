package isos.execution.graph.builder;

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

/**
 * This dependency graph builder is based on the pseudocode of the ISOS paper. The pseudocode is
 * directly interpreted as Java code, without any optimizations.
 */
public class TrivialDependencyGraphBuilder implements DependencyGraphBuilder {
  private final ConcurrentMap<SequenceNumber, Set<SequenceNumber>> committedWithDepsMap;
  private final ConcurrentMap<SequenceNumber, Boolean> executedSet;
  private final int expansionLimitSize;

  public TrivialDependencyGraphBuilder(int expansionLimitSize) {
    this.committedWithDepsMap = new ConcurrentHashMap<>();
    this.executedSet = new ConcurrentHashMap<>();
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
   * Pseudocode line 153-162
   *
   * @param v The slot for which the Dependency Graph should be calculated
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(SequenceNumber v) {
    Set<SequenceNumber> DPrime = new HashSet<>(Set.of(v)); // D'
    Set<SequenceNumber> D = new HashSet<>();
    Set<Dependency> edges = new HashSet<>();

    while (!D.equals(DPrime)) {
      // Pseudocode line 156: D := D'
      D.clear();
      D.addAll(DPrime);
      for (var seqNum : D) {
        if (!this.executedSet.containsKey(seqNum)) {
          // D' = D' UNION (UNION for all d in deps(v): (v -> d) UNION {d})
          // i.e., the dependency graph is a set of present nodes, and directed relationships of two
          // nodes.
          var slotDeps = this.committedWithDepsMap.get(seqNum);
          if (slotDeps == null) {
            continue;
          }

          // Add every dependency
          for (var d : slotDeps) {
            DPrime.add(d);
            edges.add(new Dependency(seqNum, d));
          }
        }
        // else branch: Ignore rhist in the implementation
      }
    }
    return new DependencyGraph(D, edges, true);
  }

  /**
   * !! Hot Path !! Executed slots and slots in execution window. See static method {@link
   * ExecutionUtils#executedAndExecutionWindowSlots(Set, Set, int)} for more information.
   *
   * <p>Pseudocode Name: exp_k
   *
   * @return
   */
  @Override
  public Set<SequenceNumber> getExecutionWindow() {
    return ExecutionUtils.executedAndExecutionWindowSlots(
        this.committedWithDepsMap.keySet(), this.executedSet.keySet(), this.expansionLimitSize, true);
  }

  @Override
  public Set<SequenceNumber> getExecutionWindowWithoutExecuted() {
    return ExecutionUtils.executedAndExecutionWindowSlots(
            this.committedWithDepsMap.keySet(), this.executedSet.keySet(), this.expansionLimitSize, false);
  }

  /**
   * ISOS Remark A.33:
   *
   * <p>rhist can be ignored for an implementation, as by construction it only contains executed
   * slots. An already executed slot cannot have dependencies on not yet executed slots. Therefore,
   * slots in rdeps(v) and rdepsexp(v) can be split into two sets A ⊆ executed and B ∩ executed= ∅
   * with executed and not executed slots, respectively.
   *
   * <p>Kai Remark: i.e., A are executed slots, B are not executed slots.
   *
   * <p>Only slots in B can depend on slots in A. A similar structure applies for the SCCs in
   * rdeps(v) or rdepsexp(v). As these SCCs are skipped if they were executed before, it is
   * equivalent to remove executed slots from rdeps or rdepsexp as well. The simplest way to achieve
   * that is to drop rhist completely.
   */

  /**
   * Pseudocode line 165-174
   *
   * @param v
   * @param executionWindow exp_k in pseudocode
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraphExp(
      SequenceNumber v, Set<SequenceNumber> executionWindow) {
    Set<SequenceNumber> DPrime = new HashSet<>(Set.of(v));
    DPrime.retainAll(executionWindow); // D' := {v} ∩ exp_k
    // What happens if v is outside the execution window? Nothing?
    // -> If v is not in the execution window, then return an empty dependency graph. In this case,
    // there will be another v that is inside of the execution window, as the window starts from
    // the lowest, not executed slot.
    if (DPrime.isEmpty()) {
      return new DependencyGraph(Set.of(), Set.of(), false);
    }
    Set<SequenceNumber> D = new HashSet<>();
    Set<Dependency> edges = new HashSet<>();

    while (!D.equals(DPrime)) {
      // Pseudocode line 156: D := D'
      D.clear();
      D.addAll(DPrime);
      for (var seqNum : D) {
        if (!this.executedSet.containsKey(seqNum)) {
          // D' = D' UNION (UNION for all d in deps(v), where d IN execWindow: (v -> d) UNION {d})
          // i.e., the dependency graph is a set of present nodes, and directed relationships of two
          // nodes.
          var slotDeps = this.committedWithDepsMap.get(seqNum);
          if (slotDeps == null) {
            continue;
          }

          // Add every dependency if it is in the execution window
          for (var d : slotDeps) {
            if (!executionWindow.contains(d)) {
              continue;
            }
            DPrime.add(d);
            edges.add(new Dependency(seqNum, d));
          }
        }
        // else branch: Ignore rhist in the implementation
      }
    }

    return new DependencyGraph(D, edges, true);
  }
}
