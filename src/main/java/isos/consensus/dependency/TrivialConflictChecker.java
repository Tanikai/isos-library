package isos.consensus.dependency;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.CommittedCommand;
import isos.message.client.OrderedClientRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class TrivialConflictChecker implements ConflictChecker {

  /**
   * Own representation of current agreement slots of all replicas. Kept in sync via
   * addClientRequest, overwriteClientRequest, and updateCommitedRequest.
   */
  private final Map<SequenceNumber, OrderedClientRequest> agreementSlots;

  // Conflict predicates
  BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public TrivialConflictChecker(
      BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict) {
    this.agreementSlots = new HashMap<>();
    this.defaultConflict = defaultConflict;
    this.applicationConflict = applicationConflict;
  }

  // TODO Kai: when a client request is added, maybe pass the already calculated dependency set to
  // avoid double calculation?
  @Override
  public void addClientRequest(SequenceNumber slot, OrderedClientRequest r) {
    this.agreementSlots.put(slot, r);

    // TODO Kai: When a new client request is added, update the dependency graph with new vertex and
    // edges
  }

  @Override
  public void overwriteClientRequest(SequenceNumber slot, OrderedClientRequest r) {
    this.agreementSlots.put(slot, r);

    // TODO Kai: When a client request is overwritten, update the internal dependency graph
    // accordingly (e.g. remove old vertex and edges, recalculate dependencies for this slot)
  }

  @Override
  public void updateCommitedRequest(CommittedCommand c) {
    // TODO Kai: When this function is called, update the internal dependency graph with the final
    // dependencies
    var finalDeps = c.depSet();
  }

  /**
   * Pseudocode line 66, 67
   *
   * <p>Trivial Implementation
   *
   * <p>This function is in the hot path, so performance is critical here.
   *
   * @return All agreement slots that have a DepPropose message (i.e. non-null)
   */
  @Override
  public DependencySet getCompactDependencySet(OrderedClientRequest r) {
    // Requirement: For the dependency set, the coordinator takes all known requests from both its
    // own and other replicas' agreement slots into account (see paper sec. B).

    // Optimization: Evaluate whether fork/join could be applicable here -> might be good for
    // large dependency sets
    // Answer: parallelStream() uses fork/join in background

    // TODO Kai: this.agreementSlots can basically be skipped, dependency graph should be cached
    Set<SequenceNumber> result =
        this.agreementSlots
            .entrySet()
            // allow for parallelStream() as well, as dependencies can be calculated independently
            .parallelStream()
            // if they conflict, return the sequence number, else return null for "no conflict"
            .map(
                entry -> {
                  if (this.defaultConflict.or(this.applicationConflict).test(r, entry.getValue())) {
                    return entry.getKey();
                  } else {
                    return null;
                  }
                })
            .filter(Objects::nonNull) // filter out the "no conflict"s
            .collect(Collectors.toSet());

    // Requirement: To limit the size of the set, the coordinator for each replica only includes
    // the **sequence number** of the latest conflicting request.
    // How can I get the sequence number of only the last conflicting request?
    // Approach 1: Get all conflicts, then filter out the indirect conflicts

    // TODO Kai: Determine compact dependency set via dependency graph

    return new DependencySet(result);
  }
}
