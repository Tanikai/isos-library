package isos.execution.graph.builder;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * This dependency graph builder is based on the pseudocode of the ISOS paper. The pseudocode is
 * directly interpreted as Java code, without any optimizations.
 */
public class TrivialDependencyGraphBuilder implements DependencyGraphBuilder {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public TrivialDependencyGraphBuilder() {}

  /**
   * Pseudocode line 153-162
   *
   * @param v The slot for which the Dependency Graph should be calculated
   * @param deps Mapping of an agreement slots to its dependencies
   * @param executed The set of agreement slots that are already executed
   * @return
   */
  @Override
  public DependencyGraph buildDependencyGraph(
      SequenceNumber v,
      ConcurrentMap<SequenceNumber, DependencySet> deps,
      Set<SequenceNumber> executed) {
    Set<SequenceNumber> DPrime = new HashSet<>(Set.of(v)); // D'
    Set<SequenceNumber> D = new HashSet<>();
    Set<Dependency> edges = new HashSet<>();

    // TODO Kai: should we ignore sequence numbers that we have already calculated?

    while (!D.equals(DPrime)) {
      // Pseudocode line 156: D := D'
      D.clear();
      D.addAll(DPrime);
      logger.info("Current D: {}", D);
      for (var seqNum : D) {
        if (!executed.contains(seqNum)) {
          // D' = D' UNION (UNION for all d in deps(v): (v -> d) UNION {d})
          // i.e., the dependency graph is a set of present nodes, and directed relationships of two
          // nodes.
          var slotDeps = deps.get(seqNum);
          if (slotDeps == null) {
            logger.info("seq num {} has no dependencies", seqNum);
            continue;
          }
          logger.info("deps of seq num {}: {}", seqNum, slotDeps.dependencies());

          // Add every dependency
          for (var d : slotDeps.dependencies()) {
            DPrime.add(d);
            edges.add(new Dependency(seqNum, d));
            logger.info("new edges: {}", edges);
          }
          logger.info("Added dependencies {}", slotDeps.dependencies());
        }
        // else branch: Ignore rhist in the implementation
        logger.info("current dependencies: {}", edges);
      }
    }
    logger.info("Returned dependencies: {}, Dprime: {}, edges: {}", D, DPrime, edges);
    return new DependencyGraph(D, edges);
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
      SequenceNumber v,
      Set<SequenceNumber> executionWindow,
      ConcurrentMap<SequenceNumber, DependencySet> deps,
      Set<SequenceNumber> executed) {
    Set<SequenceNumber> DPrime = new HashSet<>(Set.of(v));
    DPrime.retainAll(executionWindow); // D' := {v} ∩ exp_k
    // TODO Kai: What happens if v is outside the execution window? Nothing?
    Set<SequenceNumber> D = new HashSet<>();
    Set<Dependency> edges = new HashSet<>();

    // TODO Kai: should we ignore sequence numbers that we have already calculated?

    while (!D.equals(DPrime)) {
      // Pseudocode line 156: D := D'
      D.clear();
      D.addAll(DPrime);
      for (var seqNum : D) {
        if (!executed.contains(seqNum)) {
          // D' = D' UNION (UNION for all d in deps(v), where d IN execWindow: (v -> d) UNION {d})
          // i.e., the dependency graph is a set of present nodes, and directed relationships of two
          // nodes.
          var slotDeps = deps.get(seqNum);
          if (slotDeps == null) {
            logger.info("SeqNum {} has no dependencies / not committed", seqNum);
            continue;
          }
          logger.info("Dependencies of SeqNum {}: {}", seqNum, slotDeps.dependencies());

          // Add every dependency if it is in the execution window
          for (var d : slotDeps.dependencies()) {
            if (!executionWindow.contains(d)) {
              continue;
            }
            DPrime.add(d);
            edges.add(new Dependency(seqNum, d));
          }
          logger.info("Added dependencies {}", slotDeps.dependencies());
        }
        // else branch: Ignore rhist in the implementation
      }
      logger.info("current dependencies: {}", edges);
    }

    logger.info("Returned dependencies: {}, Dprime: {}, edges: {}", D, DPrime, edges);
    return new DependencyGraph(D, edges);
  }
}
