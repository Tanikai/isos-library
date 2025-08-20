package isos.execution.graph;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/** */
public interface DependencyGraphBuilder {
  /**
   * ISOS Paper Page 15: They return a graph consisting of slots and edges v1 → v2 between slots in
   * these graphs. By construction all slots and edges in the graph are reachable from v.
   *
   * <p>ISOS Paper: A slot s can be executed via the normal case if all slots in rdeps(s) ⊆ expk. As
   * expk by construction only includes up to k not executed slots per replica, the number of
   * dependee slots is bounded.
   */

  /**
   * Calculate the dependency graph for slot v.
   *
   * <p>Pseudocode line 152-162, function rdeps(v)
   *
   * @param v
   * @return
   */
  DependencyGraph buildDependencyGraph(
      SequenceNumber v,
      ConcurrentMap<SequenceNumber, DependencySet> deps,
      Set<SequenceNumber> executed);

  /**
   * Calculate dependency graph for slot v. Excludes slots outside the execution window.
   *
   * <p>Pseudocode line 164-174, function rdeps_{exp}(v)
   *
   * @param v
   * @param executionWindowSlots
   * @return
   */
  DependencyGraph buildDependencyGraphExp(
      SequenceNumber v,
      Set<SequenceNumber> executionWindowSlots,
      ConcurrentMap<SequenceNumber, DependencySet> deps,
      Set<SequenceNumber> executed);
}
