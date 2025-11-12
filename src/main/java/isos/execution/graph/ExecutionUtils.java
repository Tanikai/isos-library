package isos.execution.graph;

import isos.consensus.model.SequenceNumber;
import isos.execution.manager.ExecutionManager;
import isos.utils.ReplicaId;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExecutionUtils {
  /**
   * Returns all executed slots and the slots contained in the execution window.
   *
   * <p>Pseudocode Name: exp_k
   *
   * <p>Note: The execution window contains sequence numbers that are not committed / proposed yet.
   *
   * <p>Execution window: Oldest agreement slot of the coordinator with a not yet executed request.
   * Dependencies to requests beyond expansion limit are treated as missing, and block execution of
   * a request.
   *
   * <p>If a replica has not yet executed slots, the set will contain future slots that might not
   * have been committed yet (of max. expansionLimitSize length). If all slots of a replica are
   * executed, then the set will only contain the executed slots, without the execution window
   * slots.
   *
   * @param committed Committed sequence numbers / agreement slots
   * @param executed Executed sequence numbers / agreement slots
   * @param expansionLimitSize The size of the expansion limit
   * @return
   */
  public static Set<SequenceNumber> executedAndExecutionWindowSlots(
          Set<SequenceNumber> committed, Set<SequenceNumber> executed, int expansionLimitSize, boolean includeExecutedRequests) {
    // We need all slots where the sequence number is smaller than the first not executed request
    // of the replica of that sequence number plus the execution window size.
    // i.e., all v, where v.sequenceCounter < exp(v.replicaId) + k

    // First, we group the committed slots by replicaId.
    var committedByReplica =
            committed.stream()
                    .collect(Collectors.groupingBy(SequenceNumber::replicaId, Collectors.toSet()));

    // Then, we get the first not executed request for each replica
    // <ReplicaId, sequenceCounter of first not executed Request>
    Map<Integer, Integer> firstNotExecutedByReplica =
            committedByReplica.keySet().parallelStream()
                    .map(
                            replicaId ->
                                    ExecutionManager.firstNotExecutedRequestForReplica(
                                            ReplicaId.of(replicaId), committedByReplica.get(replicaId), executed))
                    .collect(Collectors.toMap(SequenceNumber::replicaId, SequenceNumber::sequenceCounter));

    var executionWindow =
            firstNotExecutedByReplica.entrySet().parallelStream()
                    .flatMap(
                            // For each replica, we generate the sequence numbers from the minimum sequence
                            // number, up to the execution window (excluding)
                            entry -> {
                              // value of entry is the lower bound -> First not executed request
                              return IntStream.range(0, entry.getValue() + expansionLimitSize)
                                      .mapToObj(seqCounter -> SequenceNumber.of(entry.getKey(), seqCounter));
                            })
                    .collect(Collectors.toSet());
    if (includeExecutedRequests) {
      executionWindow.addAll(executed);
    }

    return executionWindow;
  }

}
