package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
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
  /** How many new committed commands should be processed (and executed) at the same time */
  private final int batchProcessingMaxSize;

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
      DependencyGraphBuilder dependencyGraphBuilder,
      ExecuteInApplication executor,
      int batchProcessingMaxSize) {
    this.committed = new HashSet<>();
    this.executed = new HashSet<>();
    this.depGraphBuilder = dependencyGraphBuilder;
    this.incomingCommittedSlots = new LinkedBlockingQueue<>();
    this.deps = new ConcurrentHashMap<>();
    this.requests = new ConcurrentHashMap<>();
    this.executor = executor;
    this.batchProcessingMaxSize = batchProcessingMaxSize;
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
   * Pseudocode line 188-191
   *
   * @param scc
   */
  private void execute(List<SequenceNumber> scc) {
    for (var seqNum : ExecutionManager.sortSCCVertices(scc)) {
      var request = this.requests.get(seqNum);
      if (request == null) {
        throw new RuntimeException(
            String.format(
                "Cannot execute request with SeqNum %s, not present in requests. This is a bug.",
                seqNum));
      }
      logger.info(
          "Execute request {} with dependencies {}", seqNum, this.deps.get(seqNum).dependencies());
      this.executor.execute(request);
      this.depGraphBuilder.addExecuted(seqNum);
      this.executed.add(seqNum);
      // rhist variable is ignored
    }
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
      try {
        updateCommittedSlots();
      } catch (InterruptedException e) {
        logger.info("ExecutionManager interrupted while waiting for committed command, exiting");
        Thread.currentThread().interrupt(); // re-set interrupted flag
        break;
      }

      doNormalExecution();

      doUnblockExecution();
    } // end while loop
    logger.info("ExecutionManager was interrupted, stopping.");
  }

  /**
   * ISOS Pseudocode: Update slots committed in the meantime (Line 176)
   *
   * @throws InterruptedException
   */
  private void updateCommittedSlots() throws InterruptedException {
    // Line 176
    List<CommittedCommand> batchCommittedSlots = new ArrayList<>(this.batchProcessingMaxSize);
    CommittedCommand first = incomingCommittedSlots.take();
    batchCommittedSlots.add(first);

    incomingCommittedSlots.drainTo(batchCommittedSlots, this.batchProcessingMaxSize - 1);

    logger.debug("Process {} new committed slots", batchCommittedSlots.size());
    for (var committedCommand : batchCommittedSlots) {
      var seqNum = committedCommand.seqNum();
      this.committed.add(seqNum);
      this.depGraphBuilder.addCommittedWithDeps(seqNum, committedCommand.depSet().dependencies());
      this.deps.put(seqNum, committedCommand.depSet());
      this.requests.put(seqNum, committedCommand.clientRequest());
    }
    batchCommittedSlots.clear();
  }

  private void doNormalExecution() {
    // Line 178: Repeat loop until no further suitable v exists

    // We need to pick a v that:
    // - is in the execution window,
    // - has not been executed yet, and
    // - all of its dependencies are committed and inside the execution window

    // Normal execution case
    boolean didExecuteAgreementSlots;
    do {
      // Reset loop condition
      didExecuteAgreementSlots = false;

      // Line 178

      // The execution window should be recalculated after SCCs are executed, because the "first
      // not executed request" might change after SCC execution.
      Set<SequenceNumber> slotsInWindow = this.depGraphBuilder.getExpansionLimitSlots();

      // logger.info("Slots in window {}", slotsInWindow);

      // This has to be recalculated every time the SCCs are executed, because we do not want to
      // select agreement slots that were already executed
      Set<SequenceNumber> committedSlotsInWindowWithoutExecuted = new HashSet<>(slotsInWindow);
      committedSlotsInWindowWithoutExecuted.removeAll(this.executed);
      committedSlotsInWindowWithoutExecuted.retainAll(
          this.committed); // Only committed sequence numbers!

      if (committedSlotsInWindowWithoutExecuted.isEmpty()) {
        logger.info("Normal Case: No slots available for execution.");
        break;
      }

      // We pick a v out of the slots in window that are not executed. It has to fulfill the
      // condition that all of its dependencies are already committed and inside of the execution
      // window.
      Set<SequenceNumber> committedInExecutionWindow = new HashSet<>(slotsInWindow);
      committedInExecutionWindow.retainAll(this.committed);

      // Pick agreement slot, build its dependency graph, and check whether all dependencies are
      // in the execution window and committed
      logger.debug("Normal Case: Complete Dependency Graph");
      for (SequenceNumber v : committedSlotsInWindowWithoutExecuted) {
        // Build dependency graph
        DependencyGraph depGraph = this.depGraphBuilder.buildDependencyGraph(v);

        // Checking whether all dependencies are contained in the vertices is wrong. Instead, we
        // have to check whether all **edge destinations** are contained in the execution window.
        // If any dependencies are not committed, we cannot proceed.
        // Edge Structure: Sequence Number -> Dependency
        var slotDependencies =
            depGraph.edges().stream().map(Dependency::to).collect(Collectors.toSet());

        if (!committedInExecutionWindow.containsAll(slotDependencies)) {
          // Dependency Graph of v contains agreement slots that have not yet been committed
          // -> we have to skip this v and choose next one
          continue;
        }
        logger.debug(
            "All dependencies {} of sequence number {} are committed and in execution window.",
            slotDependencies,
            v);

        // We have a v where all dependencies are committed

        // Now: Find not yet executed SCCs in rdeps(v) in inverse topological order
        List<Set<SequenceNumber>> SCCs = DependencyGraph.TarjanSCCDepGraph(depGraph);

        for (Set<SequenceNumber> scc : SCCs) {
          // Line 178: Normal case execution
          // Because the Dependency Graph can contain slots that are already executed, we have to
          // filter out the already executed ones
          // Ordering of vertices in the SCC for request execution is done in the execute function
          logger.info("Normal case: execute SCC with sequence numbers {}", scc);
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
  }

  private void doUnblockExecution() {
    // Line 183: Unblock execution case

    boolean didExecuteAgreementSlots;
    do {
      // Reset loop condition
      didExecuteAgreementSlots = false;

      Set<SequenceNumber> slotsInWindow = this.depGraphBuilder.getExpansionLimitSlots();
      Set<SequenceNumber> committedSlotsInWindowWithoutExecuted = new HashSet<>(slotsInWindow);
      committedSlotsInWindowWithoutExecuted.removeAll(this.executed);
      committedSlotsInWindowWithoutExecuted.retainAll(this.committed);

      if (committedSlotsInWindowWithoutExecuted.isEmpty()) {
        logger.debug("Unblock case: No slots available for execution.");
        break;
      }

      logger.debug("Unblock Case: Dependency Graph with execution window limit");
      for (SequenceNumber v : committedSlotsInWindowWithoutExecuted) {
        // Build dependency graph, but excludes slots outside the execution window
        DependencyGraph depGraph = this.depGraphBuilder.buildDependencyGraphExp(v, slotsInWindow);
        var slotDependencies =
            depGraph.edges().stream().map(Dependency::to).collect(Collectors.toSet());

        // We don't use the intersection set of committed and the execution window here
        if (!committed.containsAll(slotDependencies)) {
          // Dependency Graph of v contains agreement slots that have not yet been committed
          // -> we have to skip this v and choose next one
          continue;
        }
        // We have a v where all dependencies (without deps that are outside the execution window)
        // are committed

        List<Set<SequenceNumber>> SCCs = DependencyGraph.TarjanSCCDepGraph(depGraph);

        try {
          // Line 186
          var firstSCC = SCCs.getFirst();
          logger.info("Unblock case: execute only first SCC with sequence numbers {}", firstSCC);
          this.execute(
              firstSCC.stream().filter(element -> !this.executed.contains(element)).toList());

          // After we have executed a single case for the unblock, we can return
          return;
        } catch (NoSuchElementException e) {
          //
        }
      }

    } while (didExecuteAgreementSlots);
  }

  /**
   * Pseudocode name: exp(r_i)
   *
   * <p>First not executed request for replica r_i. Defines the lower bound of the execution window.
   *
   * @param replicaId
   * @param committed
   * @param executed
   * @return If the stream is empty, returns empty optional. Else, returns the smallest sequence
   *     number.
   */
  public static SequenceNumber firstNotExecutedRequestForReplica(
      ReplicaId replicaId, Set<SequenceNumber> committed, Set<SequenceNumber> executed) {
    // Get all not executed Sequence Numbers
    var notExecutedByReplica =
        committed.parallelStream()
            .filter(
                seqNum -> seqNum.replicaId() == replicaId.value() && !executed.contains(seqNum));

    return notExecutedByReplica
        .min(SequenceNumber::compareTo)
        .orElseGet(
            () -> {
              // If all sequence numbers are already executed, then return the highest committed seq
              // num
              // If committed is empty, return the first sequence number
              return committed.stream()
                  .max(Comparator.naturalOrder())
                  .orElseGet(() -> SequenceNumber.of(replicaId, 0));
            });
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
  public static List<SequenceNumber> sortSCCVertices(List<SequenceNumber> scc) {
    var result = new ArrayList<>(scc);
    result.sort(
        Comparator.comparingInt(SequenceNumber::sequenceCounter)
            .thenComparing(SequenceNumber::replicaId));
    return result;
  }
}
