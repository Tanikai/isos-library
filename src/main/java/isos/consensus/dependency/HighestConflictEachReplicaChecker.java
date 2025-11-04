package isos.consensus.dependency;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.CommittedCommand;
import isos.message.replica.ClientRequestContainer;
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
          ReplicaId, ConcurrentNavigableMap<SequenceNumber, ClientRequestContainer>>
      agreementSlots;

  // Conflict predicates
  BiPredicate<ClientRequestContainer, ClientRequestContainer> defaultConflict;
  BiPredicate<ClientRequestContainer, ClientRequestContainer> applicationConflict;

  public HighestConflictEachReplicaChecker(
      BiPredicate<ClientRequestContainer, ClientRequestContainer> defaultConflict,
      BiPredicate<ClientRequestContainer, ClientRequestContainer> applicationConflict) {
    this.defaultConflict = defaultConflict;
    this.applicationConflict = applicationConflict;

    this.agreementSlots = new ConcurrentHashMap<>();
  }

  @Override
  public void addClientRequest(SequenceNumber slot, ClientRequestContainer r, DependencySet deps) {
    var depsByReplicaId =
        this.agreementSlots.computeIfAbsent(
            slot.replicaIdRec(), x -> new ConcurrentSkipListMap<>());
    depsByReplicaId.put(slot, r);
  }

  @Override
  public void overwriteClientRequest(SequenceNumber slot, ClientRequestContainer r) {
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
  public DependencySet getCompactDependencySet(SequenceNumber seqNum, ClientRequestContainer r) {
    Set<SequenceNumber> depSet =
        this.agreementSlots.entrySet().parallelStream()
            .map(
                entry -> {
                  // For each replicaId, we iterate through the dependencies top->down
                  // (newest->oldest), and return the first conflict
                  NavigableMap<SequenceNumber, ClientRequestContainer> requests =
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
