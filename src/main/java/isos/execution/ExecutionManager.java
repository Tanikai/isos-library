package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.DependencyGraphBuilder;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionManager implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final Logger staticLogger = LoggerFactory.getLogger("ExecutionManagerStatic");

  // Variables at each replica
  /** Size of execution window, k in pseudocode */
  private int executionWindowSize;

  // Sets containing all slots which have been committed or executed so far
  private final Set<SequenceNumber> committed;
  private final Set<SequenceNumber> executed;

  private final DependencyGraphBuilder depGraphBuilder;

  private final BlockingQueue<ExecuteMessage> incomingCommittedSlots;

  private final ExecuteInApplication executor;

  /** Returns the dependencies for a given slot. */
  private final ConcurrentMap<SequenceNumber, DependencySet> deps;

  private final ConcurrentMap<SequenceNumber, OrderedClientRequest> requests;

  public ExecutionManager(
      int executionWindowSize,
      DependencyGraphBuilder dependencyGraphBuilder,
      ExecuteInApplication executor) {
    this.committed = new HashSet<>();
    this.executed = new HashSet<>();
    this.executionWindowSize = executionWindowSize;
    this.depGraphBuilder = dependencyGraphBuilder;
    this.incomingCommittedSlots = new LinkedBlockingQueue<>();
    this.deps = new ConcurrentHashMap<>();
    this.requests = new ConcurrentHashMap<>();
    this.executor = executor;
  }

  public boolean submitCommittedRequest(ExecuteMessage r) {
    return this.incomingCommittedSlots.offer(r);
  }

  /**
   * Pseudocode name: exp
   *
   * <p>First not executed request for replica r_i, defined the lower bound of the execution window
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
    var notExecutedByReplica =
        committed.parallelStream()
            .filter(
                seqNum -> seqNum.replicaId() == replicaId.value() && !executed.contains(seqNum));

    return notExecutedByReplica.min(SequenceNumber::compareTo);
  }

  /**
   * Executed slots and slots in execution window
   *
   * <p>Pseudocode Name: exp_k
   *
   * @return
   */
  private Set<SequenceNumber> slotsInExecutionWindow() {
    return ExecutionManager.slotsInExecutionWindow(
        this.committed, this.executed, this.executionWindowSize);
  }

  /**
   * Returns executed slots and slots in execution window.
   *
   * <p>Used in pseudocode 178 and 184.
   *
   * @return
   */
  private Set<SequenceNumber> slotsInExecutionWindowWithoutExecuted() {
    return null;
  }

  // Execution window:
  // Oldest agreement slot of the coordinator with a not yet executed request.
  // Dependencies to requests beyond expansion limit are treated as missing, and block execution of
  // a request

  public static Set<SequenceNumber> slotsInExecutionWindow(
      Set<SequenceNumber> committed, Set<SequenceNumber> executed, int executionWindowSize) {
    // We need all slots where the sequence sequence is smaller than the first not executed request
    // of the replica of that sequence number plus the execution window size.
    // i.e., all v, where v.sequenceCounter < exp(v.replicaId) + k

    // First, we group the committed slots by replicaId
    var committedByReplica =
        committed.stream()
            .collect(Collectors.groupingBy(SequenceNumber::replicaId, Collectors.toSet()));

    // Then, we get the first not executed request for each replica
    Map<Integer, Integer> firstNotExecutedByReplica =
        committedByReplica.keySet().parallelStream()
            .map(
                replicaId ->
                    ExecutionManager.firstNotExecutedRequestForReplica(
                        new ReplicaId(replicaId), committedByReplica.get(replicaId), executed))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(SequenceNumber::replicaId, SequenceNumber::sequenceCounter));

    // Now, we can filter the committedByReplica values by the sequenceNumber of the
    // firstNotExecuted + executionWindowSize

    var slotsInWindow =
        committed.stream()
            .filter(
                slot -> {
                  var minSeqNum = firstNotExecutedByReplica.get(slot.replicaId());
                  if (minSeqNum == null) {
                    staticLogger.error(
                        "ReplicaId {} does not have a minimum sequence number!", slot.replicaId());
                    return false;
                  }

                  // v.seqNum_replicaId < exp(v.replicaId) + k
                  return slot.sequenceCounter() < (minSeqNum + executionWindowSize);
                })
            .collect(Collectors.toSet());

    return slotsInWindow;
  }

  /** Pseudocode line 175-188 */
  @Override
  public void run() {
    logger.info("Start ExecutionManager loop");
    while (!Thread.currentThread().isInterrupted()) {
      // Update slots committed in the meantime
      ExecuteMessage receivedMessage;
      while ((receivedMessage = incomingCommittedSlots.poll()) != null) {
        var seqNum = receivedMessage.seqNum();
        this.committed.add(seqNum);
        this.deps.put(seqNum, receivedMessage.depSet());
        this.requests.put(seqNum, receivedMessage.clientRequest());
      }

      // Repeat for loop until no further suitable v exists
      // Normal case execution
      var slots = this.slotsInExecutionWindowWithoutExecuted();
      // Filter slots where the dependency graph of the slot is a subset of committed (i.e., all
      // slots in the dependency graph have been committed)

      // Execute the commands
      //      this.executor.execute();

      // Unblock execution case
    }
    logger.info("ExecutionManager was interrupted, stopping.");
  }
}
