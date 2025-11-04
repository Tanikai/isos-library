package isos.execution.graph;

import isos.consensus.model.SequenceNumber;

import java.util.Set;

/**
 * The DependencyGraphBuilder interface is used to build a dependency graph from committed and
 * executed agreement slots, in order to determine the execution order of requests. For the
 * dependency graph builder that is used to get the direct dependencies of a request (required to
 * determine the compact dependency set), see {@link isos.consensus.dependency.ConflictChecker}.
 */
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
   * Update the slot dependencies with the given deps.
   *
   * @param seqNum
   * @param deps
   */
  void addCommittedWithDeps(SequenceNumber seqNum, Set<SequenceNumber> deps);

  /**
   * Add slot to the set of agreement slots that are already executed
   * @param executedSlot
   */
  void addExecuted(SequenceNumber executedSlot);

  /**
   * Calculate the dependency graph for slot v.
   *
   * <p>Pseudocode line 152-162, function rdeps(v)
   *
   * @param v
   * @return
   */
  DependencyGraph buildDependencyGraph(SequenceNumber v);

  /**
   * Executed slots plus slots in expansion limit.
   *
   * Pseudocode: exp_k
   *
   * @return
   */
  Set<SequenceNumber> getExecutionWindow();

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
      Set<SequenceNumber> executionWindowSlots);
}
