package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.DependencyGraph;
import isos.execution.graph.DependencyGraphBuilder;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class ExecutionManager implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final Logger staticLogger = LoggerFactory.getLogger("ExecutionManagerStatic");

  // Variables at each replica
  /** Size of execution window, k in pseudocode */
  private final int executionWindowSize;

  // Sets containing all slots which have been committed or executed so far
  private final Set<SequenceNumber> committed;
  private final Set<SequenceNumber> executed;

  private final DependencyGraphBuilder depGraphBuilder;

  private final BlockingQueue<CommittedCommand> incomingCommittedSlots;

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

  /**
   * !! Hot path !!
   *
   * <p>Pseudocode line 175-188
   */
  @Override
  public void run() {
    logger.info("Start ExecutionManager loop");
    while (!Thread.currentThread().isInterrupted()) {
      // Update slots committed in the meantime
      CommittedCommand receivedMessage;

      // TODO Kai: this should be delegated to somewhere else, so that we can use the
      // DependencyGraph optimizations (
      while ((receivedMessage = incomingCommittedSlots.poll()) != null) {
        var seqNum = receivedMessage.seqNum();
        this.committed.add(seqNum);
        this.deps.put(seqNum, receivedMessage.depSet());
        this.requests.put(seqNum, receivedMessage.clientRequest());
      }

      // Line 178: Repeat loop until no further suitable v exists

      // We need to pick a v that:
      // - is in the execution window,
      // - has not been executed yet, and
      // - all of its dependencies are committed and inside the execution window

      // Normal execution case
      boolean didExecuteAgreementSlots = false;
      do {
        // The execution window should be recalculated after SCCs are executed, because the "first
        // not executed request" might change after SCC execution.
        Set<SequenceNumber> slotsInWindow = this.slotsInExecutionWindow();

        // This has to be recalculated every time the SCCs are executed, because we do not want to
        // select agreement slots that were already executed (waste of CPU cycles)
        Set<SequenceNumber> slotsInWindowWithoutExecuted = new HashSet<>(slotsInWindow);
        slotsInWindowWithoutExecuted.removeAll(this.executed);

        // We pick a v out of the slots in window that are not executed. It has to fulfill the
        // condition that all of its dependencies are already committed and inside of the execution
        // window.
        Set<SequenceNumber> committedInExecutionWindow = new HashSet<>(slotsInWindow);
        committedInExecutionWindow.retainAll(this.committed);

        // Pick agreement slot, build its dependency graph, and check whether all dependencies are
        // in the execution window and committed
        for (SequenceNumber v : slotsInWindowWithoutExecuted) {
          // Build dependency graph
          DependencyGraph depGraph =
              this.depGraphBuilder.buildDependencyGraph(v, this.deps, this.executed);

          if (!committedInExecutionWindow.containsAll(depGraph.slots())) {
            // Dependency Graph of v contains agreement slots that have not yet been committed
            // -> we have to skip this v and choose next one
            continue;
          }

          // We have a v where all dependencies are committed

          // Now: Find not yet executed SCCs in rdeps(v) in inverse topological order
          List<Set<SequenceNumber>> SCCs = DependencyGraph.TarjanSCCDepGraph(depGraph);

          for (Set<SequenceNumber> scc : SCCs) {
            // Line 178: Normal case execution
            // Because the Dependency Graph can contain slots that are already executed, we have to
            // filter out the already executed ones
            // Ordering of vertices in the SCC for request execution is done in the execute function
            this.execute(scc.stream().filter(element -> !this.executed.contains(element)).toList());
            didExecuteAgreementSlots = true;
          }

          // We have executed some slots. Now we have to recalculate the execution window
          break;
        }
        // We want to repeat this loop until no further suitable v exists, i.e., we iterate over all
        // committed sequence numbers v, but we didn't execute any slot, because the dependencies of
        // each v is not fully contained in the execution window.
      } while (didExecuteAgreementSlots);

      // Line 183: Unblock execution case
      didExecuteAgreementSlots = false;
      do {
        Set<SequenceNumber> slotsInWindow = this.slotsInExecutionWindow();
        Set<SequenceNumber> slotsInWindowWithoutExecuted = new HashSet<>(slotsInWindow);
        slotsInWindowWithoutExecuted.removeAll(this.executed);

        for (SequenceNumber v : slotsInWindowWithoutExecuted) {
          // Build dependency graph, but excludes slots outside the execution window
          DependencyGraph depGraph =
              this.depGraphBuilder.buildDependencyGraphExp(
                  v, slotsInWindow, this.deps, this.executed);

          // We don't use the intersection set of committed and the execution window here
          if (!committed.containsAll(depGraph.slots())) {
            // Dependency Graph of v contains agreement slots that have not yet been committed
            // -> we have to skip this v and choose next one
            continue;
          }
          // We have a v where all dependencies are committed

          List<Set<SequenceNumber>> SCCs = DependencyGraph.TarjanSCCDepGraph(depGraph);

          try {
            // Line 186
            var firstSCC = SCCs.getFirst();
            this.execute(
                firstSCC.stream().filter(element -> !this.executed.contains(element)).toList());
            didExecuteAgreementSlots = true;
            // We have executed some slots. Now we have to recalculate the execution window
            break;
          } catch (NoSuchElementException e) {
            //
          }
        }

      } while (didExecuteAgreementSlots);
    }
    logger.info("ExecutionManager was interrupted, stopping.");
  }

  /**
   * Thread-safe
   *
   * @param r
   * @return
   */
  public boolean submitCommittedRequest(CommittedCommand r) {
    return this.incomingCommittedSlots.offer(r);
  }

  /**
   * Pseudocode name: exp(r_i)
   *
   * <p>First not executed request for replica r_i. Defines the lower bound of the execution window.
   *
   * @param replicaId
   * @return The sequence number of the smallest not yet executed agreement slot of replicaId.
   * @throws NoSuchElementException If the smallest sequence number could not be found. This can be
   *     the case if there are no
   */
  private SequenceNumber firstNotExecutedRequestForReplica(ReplicaId replicaId)
      throws NoSuchElementException {
    return ExecutionManager.firstNotExecutedRequestForReplica(
            replicaId, this.committed, this.executed)
        .orElseThrow();
  }

  /**
   * Pseudocode name: exp(r_i)
   *
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
   * !! Hot Path !!
   *
   * <p>Executed slots and slots in execution window.
   *
   * <p>Returns all agreement slots where its sequence number is smaller than the
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

  /**
   * @param committed
   * @param executed
   * @param executionWindowSize
   * @return
   */
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

  /**
   * Pseudocode line 188-191
   *
   * @param scc
   */
  private void execute(List<SequenceNumber> scc) {
    for (var seqNum : ExecutionManager.sortSCC(scc)) {
      var request = this.requests.get(seqNum);
      if (request == null) {
        logger.error(
            "Wanted to execute request {}, but not found in requests. This should not happen",
            seqNum);
        continue;
      }
      this.executor.execute(request);
      this.executed.add(seqNum);
      // rhist variable is ignored
    }
  }

  /**
   * Pseudocode line 193-194
   *
   * <p>Returns the SequenceNumbers sorted primarily by the counter, and use ReplicaId as tie
   * breaker when counter is same
   *
   * @param scc The strongly connected components of the dependency graph.
   * @return A sorted copy of scc.
   */
  public static List<SequenceNumber> sortSCC(List<SequenceNumber> scc) {
    var result = new ArrayList<>(scc);
    result.sort(
        Comparator.comparingInt(SequenceNumber::sequenceCounter)
            .thenComparing(SequenceNumber::replicaId));
    return result;
  }
}
