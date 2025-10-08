package isos.consensus.dependency;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.CommittedCommand;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * This conflict checker implements the strategy where the highest conflict of each replica is
 * returned as the compact dependency set. This 
 */
public class HighestConflictEachReplicaChecker implements ConflictChecker {
  private final ConcurrentMap<
          ReplicaId, ConcurrentNavigableMap<SequenceNumber, OrderedClientRequest>>
      agreementSlots;

  // Conflict predicates
  BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public HighestConflictEachReplicaChecker(
      BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict) {
    this.defaultConflict = defaultConflict;
    this.applicationConflict = applicationConflict;

    this.agreementSlots = new ConcurrentHashMap<>();
  }

  @Override
  public void addClientRequest(SequenceNumber slot, OrderedClientRequest r, DependencySet deps) {
    var depsByReplicaId =
        this.agreementSlots.computeIfAbsent(
            slot.replicaIdRec(), x -> new ConcurrentSkipListMap<>());
    depsByReplicaId.put(slot, r);
  }

  @Override
  public void overwriteClientRequest(SequenceNumber slot, OrderedClientRequest r) {
    var depsByReplicaId =
        this.agreementSlots.computeIfAbsent(
            slot.replicaIdRec(), x -> new ConcurrentSkipListMap<>());
    depsByReplicaId.put(slot, r);
  }

  @Override
  public void updateCommitedRequest(CommittedCommand c) {
    // Because the HighestConflictChecker does not build a dependency graph to determine the
    // compact dependency set, we do not need to update the dependencies when a command is committed
  }

  @Override
  public DependencySet getCompactDependencySet(SequenceNumber seqNum, OrderedClientRequest r) {
    Set<SequenceNumber> depSet =
        this.agreementSlots.entrySet().parallelStream()
            .map(
                entry -> {
                  // For each replicaId, we iterate through the dependencies top->down
                  // (newest->oldest), and return the first conflict
                  NavigableMap<SequenceNumber, OrderedClientRequest> requests =
                      entry.getValue().descendingMap();
                  for (var candidate : requests.entrySet()) {
                    if (this.defaultConflict
                        .or(this.applicationConflict)
                        .test(r, candidate.getValue())) {
                      return candidate.getKey();
                    }
                  }
                  return null;
                })
            .filter(Objects::isNull)
            .collect(Collectors.toSet());

    return new DependencySet(depSet);
  }
}
