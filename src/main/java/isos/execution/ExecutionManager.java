package isos.execution;

import isos.consensus.model.SequenceNumber;
import isos.execution.graph.DependencyGraphBuilder;
import isos.utils.ReplicaId;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class ExecutionManager implements Runnable {

  // Variables at each replica
  /** Size of execution window, k in pseudocode */
  private int executionWindowSize;

  // Sets containing all slots which have been committed or executed so far
  private Set<SequenceNumber> committed;
  private Set<SequenceNumber> executed;

  private final DependencyGraphBuilder depGraphBuilder;

  private final BlockingQueue<ExecuteMessage> incomingCommittedSlots;

  public ExecutionManager(int executionWindowSize, DependencyGraphBuilder dependencyGraphBuilder) {
    this.executionWindowSize = executionWindowSize;
    this.depGraphBuilder = dependencyGraphBuilder;
    this.incomingCommittedSlots = new LinkedBlockingQueue<>();
  }

  public boolean submitCommittedRequest(ExecuteMessage r) {
    return this.incomingCommittedSlots.offer(r);
  }

  /**
   * First not executed request for replica r_i, defined the lower bound of the execution window
   *
   * @param replicaId
   * @return
   */
  private SequenceNumber firstNotExecutedRequestForReplica(ReplicaId replicaId)
      throws NoSuchElementException {
    return ExecutionManager.firstNotExecutedRequestForReplica(
            replicaId, this.committed, this.executed)
        .orElseThrow();
  }

  /**
   * @param replicaId
   * @param committed
   * @param executed
   * @return If the stream is empty, returns empty optional. Else, returns the smallest sequence
   *     number.
   */
  public static Optional<SequenceNumber> firstNotExecutedRequestForReplica(
      ReplicaId replicaId, Set<SequenceNumber> committed, Set<SequenceNumber> executed) {
    // Get all not executed Sequence Numbers
    var result = new HashSet<>(committed);
    result.removeAll(executed);

    return result.stream().min(SequenceNumber::compareTo);
  }

  /**
   * Executed slots and slots in execution window
   *
   * @return
   */
  private Set<SequenceNumber> slotsInExecutionWindow(SequenceNumber v) {
    return ExecutionManager.slotsInExecutionWindow(
        v.replicaIdRec(), this.committed, this.executed, this.executionWindowSize);
  }

  // Execution window:
  // Oldest agreement slot of the coordinator with a not yet executed request.
  // Dependencies to requests beyond expansion limit are treated as missing, and block execution of
  // a request

  public static Set<SequenceNumber> slotsInExecutionWindow(
      ReplicaId replicaId,
      Set<SequenceNumber> committed,
      Set<SequenceNumber> executed,
      int executionWindowSize) {
    var minRequest =
        ExecutionManager.firstNotExecutedRequestForReplica(replicaId, committed, executed);

    if (minRequest.isEmpty()) {
      throw new IllegalArgumentException("could not determine the lower bound of execution window");
    }

    // Should be cached / returned by notExecutedRequest
    var result = new HashSet<>(committed);
    result.removeAll(executed);

    var minNotExecuted = minRequest.get();
    var maxSeqNum =
        new SequenceNumber(replicaId, minNotExecuted.sequenceCounter() + executionWindowSize);

    return result.stream()
        .filter(seqNum -> seqNum.sequenceCounter() < maxSeqNum.sequenceCounter())
        .collect(Collectors.toSet());
  }

  @Override
  public void run() {}
}
