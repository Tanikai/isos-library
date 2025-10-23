package isos.consensus;

import isos.communication.MessageSender;
import isos.consensus.buffer.ISOSMessageBuffer;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.model.*;
import isos.consensus.model.viewchange.CertificateType;
import isos.consensus.model.viewchange.DepProposeAndDepVerifys;
import isos.consensus.model.viewchange.FastPathCertificate;
import isos.consensus.model.viewchange.ReconciliationPathCertificate;
import isos.execution.CommittedCommand;
import isos.execution.ExecutableRequestReceiver;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.TimeoutMessage;
import isos.message.replica.fast.DepCommitMessage;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.reconciliation.CommitMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.message.replica.viewchange.ExecMessage;
import isos.message.replica.viewchange.NewViewMessage;
import isos.message.replica.viewchange.QueryExecMessage;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * The AgreementSlotQueueProcessor handles incoming messages and delegates them to subtasks,
 * depending on the current state. In other words, it contains and executes the (core) business
 * logic of ISOS. Additionally, it handles timeouts and their logic if they expire.
 */
public class AgmtSlotQueueProcessor implements Runnable {
  private final Logger logger;

  // --- Attributes ---
  /** Id of the *running* replica, not of the agreement slot */
  private final ReplicaId ownReplicaId;

  /** Sequence number that this queue processor is responsible for */
  private final SequenceNumber seqNum;

  // --- Messaging ---
  /** Queue of incoming messages */
  private final BlockingDeque<ISOSMessage> incomingQueue;

  /**
   * Messages that have been deferred because we were not in the correct step yet. After the
   * preconditions are met, they can be processed.
   */
  private final ISOSMessageBuffer bufferedMessages;

  // --- Timeouts ---
  private final TimeoutConfiguration timeoutConfig;
  private final ScheduledExecutorService timeoutExecutor;
  private final Map<ISOSTimeoutType, ScheduledFuture<?>> currentTimeouts;

  /** Is accessed concurrently by the SlotProcessor and Timeout tasks. */
  private final ConcurrentMap<ISOSTimeoutType, TimeoutState> timeoutStates;

  // --- AgreementSlot State ---
  /**
   * AgreementSlot passed from the AgreementSlotManager. If the request != null upon thread start,
   * we assume that we are the coordinator. This processor is only reading from / writing to this
   * slot. However, the AgreementSlotManager is able to read this slot.
   */
  private final AgreementSlot slot;

  // --- Outside Dependencies ---
  /** Callback function to broadcast messages to replicas */
  private final MessageSender msgSender;

  /**
   * The conflictChecker returns the conflicts of a ClientRequest to all other ClientRequests that
   * we have already received.
   *
   * <p>Passed from the outside due to access to other agreement slots.
   */
  private final ConflictChecker conflictChecker;

  /** Passed from the outside due to access to other agreement slots. */
  private final DependencyWaitFunction dependencyWait;

  /**
   * Passed from the outside because request execution is not the responsibility of the
   * AgreementQueueProcessor
   */
  private final ExecutableRequestReceiver requestExecutor;

  private final int maxFaults;
  private final int replicaCount;

  public AgmtSlotQueueProcessor(
      ReplicaId ownReplicaId,
      SequenceNumber seqNum,
      BlockingDeque<ISOSMessage> incomingQueue,
      TimeoutConfiguration timeoutConfig,
      MessageSender msgSender,
      AgreementSlot slot,
      ConflictChecker conflictChecker,
      DependencyWaitFunction dependencyWait,
      ExecutableRequestReceiver requestExecutor,
      int maxFaults,
      int replicaCount) {
    // Attributes
    this.ownReplicaId = ownReplicaId;
    this.seqNum = seqNum;

    // Messaging
    this.incomingQueue = incomingQueue;
    this.bufferedMessages = new ISOSMessageBuffer();

    // Timeouts
    this.timeoutConfig = timeoutConfig;
    this.timeoutExecutor =
        new ScheduledThreadPoolExecutor(2); // TODO Kai: how to determine the corePoolSize?
    this.currentTimeouts = new HashMap<>();
    this.timeoutStates = new ConcurrentHashMap<>();

    // AgreementSlot State
    this.slot = slot;

    // Outside Dependencies
    this.msgSender = msgSender;
    this.conflictChecker = conflictChecker;
    this.dependencyWait = dependencyWait;
    this.requestExecutor = requestExecutor;

    this.maxFaults = maxFaults;
    this.replicaCount = replicaCount;

    this.logger = LoggerFactory.getLogger(String.format("QueueProcessor %s", seqNum.toString()));
  }

  /** Processes incoming messages from the queue in a loop. */
  @Override
  public void run() {
    // We have to differentiate whether the Queue Processor was created due to a received
    // ClientRequest, or just a Replica Message

    // When we received a ClientRequest, the AgreementSlot request is already populated
    if (this.slot.getRequest() != null) {
      handleClientRequest();
    }

    while (!Thread.currentThread().isInterrupted()) {
      try {
        ISOSMessage msg = this.incomingQueue.take();

        // Do not check timeout messages for preconditions
        if (msg instanceof TimeoutMessage timeoutMsg) {
          this.handleTimeoutMessage(timeoutMsg);
          continue;
        }

        try {
          if (!this.stepPrecondHolds(msg.msgType())) {
            logger.warn(
                    "Step mismatch when processing {}, current step {}",
                    msg.msgType(),
                    this.slot.getStep());
            // Defer processing of messages if preconditions do not hold.
            if (msg.msgType() != ISOSMessageType.DEP_PROPOSE_WITH_REQ) {
              this.bufferedMessages.bufferMessage(msg);
            }
            continue;
          }
        } catch (AssertionError e) {
          logger.error("Assertion has failed, throwing message away. Reason: {}", e.getMessage());
          continue;
        }

        // If the preconditions hold and all asserts passed, we can handle the message normally.
        this.handleMessage(msg);
      } catch (InterruptedException e) {
        // interrupted while waiting to take new message from incomingQueue
        logger.info("Interrupted while waiting for message in incomingQueue. Exiting.");
        Thread.currentThread().interrupt(); // Re-interrupt to keep interrupted status
        break;
      }
    }
    logger.info("QueueProcessor has been stopped.");
  }

  /**
   * Pseudocode line 60
   *
   * @param maxFaults
   * @throws InterruptedException
   */
  public void awaitConditionCompleted(int maxFaults) throws InterruptedException {
    if (maxFaults < 0) {
      throw new IllegalArgumentException("Maximum faults is smaller than 0");
    } else if (maxFaults == 0) {
      logger.warn("Maximum faults is 0. Is ISOS configured correctly?");
    }
    this.slot.awaitConditionCompleted(maxFaults + 1);
  }

  /**
   * In order to keep the {@link #run()} method clean, this function does the switch/case for
   * incoming messages from the inputQueue.
   *
   * <p>TODO Kai: instead of this long if else, maybe use registry/hashmap with class->function
   * mapping?
   *
   * @param msg
   */
  private void handleMessage(ISOSMessage msg) {
    logger.debug(
        "Queue received message of type {} from sender {}", msg.msgType(), msg.logicalSender());

    // Use different handlers depending on the received message
    switch (msg) {
      // Fast path
      case DepProposeWithRequest depPropose ->
          // This case only happens if we receive a depPropose from another replica. For requests
          // where
          // the current replica acts as the coordinator, see handleReceivedClientRequest().
          this.handleDepProposeWithRequest(depPropose);
      case DepProposeMessage ignored ->
          logger.error("Received DepPropose message without request, throwing away");
      case DepVerifyMessage depVerify -> this.handleDepVerify(depVerify);
      case DepCommitMessage depCommit -> this.handleDepCommit(depCommit);

      // Reconciliation path
      case PrepareMessage prepare -> this.handlePrepareMessage(prepare);
      case CommitMessage commit -> this.handleCommitMessage(commit);

      // View change
      case NewViewMessage newView -> this.handleNewViewMessage(newView);
      case ViewChangeMessage viewChange -> this.handleViewChangeMessage(viewChange);
      case QueryExecMessage queryExec -> this.handleQueryExecMessage(queryExec);
      case ExecMessage exec -> this.handleExecMessage(exec);
      default -> {}
    }
  }

  /**
   * Called when our replica receives a request from a client and has to act as the coordinator for
   * that request.
   *
   * <p>Pseudocode Line 10-19
   */
  private void handleClientRequest() {
    // We can only be coordinator of a client request if the generated SequenceNumber is our own
    assert Objects.equals(this.ownReplicaId, this.seqNum.replicaIdRec());
    var r = slot.getRequest();

    DependencySet depSet = this.conflictChecker.getCompactDependencySet(this.seqNum, r);
    Set<ReplicaId> followerSet = msgSender.getLowestPingReplicas(2 * this.maxFaults);
    DepProposeMessage propose =
        new DepProposeMessage(seqNum, ownReplicaId, r.calculateHash(), depSet, followerSet);

    DepProposeWithRequest dp =
        new DepProposeWithRequest(propose, r); // Create wrapper message that includes the request

    this.slot.setDepPropose(propose);
    this.slot.setRequest(r);
    this.conflictChecker.addClientRequest(this.seqNum, r, depSet);
    this.slot.setStep(AgreementSlotPhase.PROPOSED);
    // we have changed the messages in the slot, so signal all waiting threads

    var msg = new ISOSMessageWrapper(dp, this.ownReplicaId.value());

    // Broadcast to all replicas
    this.msgSender.broadcastToReplicas(false, msg);

    this.startTimeout(ISOSTimeoutType.COMMIT);
  }

  // region Timeouts

  private void startTimeout(ISOSTimeoutType timeoutType) {
    var currentState = this.timeoutStates.getOrDefault(timeoutType, TimeoutState.NULL);

    // NULL -> we create new timeout
    // STARTED -> running, we do not create new timeout
    // EXPIRED -> we want to recreate the timeout
    // CANCELED -> see expired
    if (currentState.equals(TimeoutState.STARTED)) {
      logger.error(
          "Timeout of type {} has already been started: {}. Do not create new timeout",
          timeoutType,
          currentState);
      return;
    }

    if (this.currentTimeouts.containsKey(timeoutType)) {
      logger.warn(
          "A timeout of type {} with the state {} already exists!", timeoutType, currentState);
    }

    var timeout =
        this.timeoutExecutor.schedule(
            () ->
                this.incomingQueue.addFirst(
                    new TimeoutMessage(timeoutType, this.seqNum, this.ownReplicaId)),
            timeoutConfig.getTimeoutDurationByType(timeoutType),
            TimeUnit.MILLISECONDS);
    this.currentTimeouts.put(timeoutType, timeout);
    this.timeoutStates.put(timeoutType, TimeoutState.STARTED);
  }

  /**
   * When a timeout expires, a TimeoutMessage is added to the incomingQueue, so that the logic is
   * executed sequentially by the processor. (Timeout expiry is done by another thread)
   *
   * @param timeoutMessage
   */
  private void handleTimeoutMessage(TimeoutMessage timeoutMessage) {
    // We have to check whether the timeout was already canceled by a previous message or not
    logger.debug("Handle {} timeout", timeoutMessage.timeoutType());
    var timeoutType = timeoutMessage.timeoutType();
    var currentState = this.timeoutStates.get(timeoutType);
    if (currentState.equals(TimeoutState.CANCELED)) {
      logger.warn(
          "Received timeout message of type {}, but already set to canceled. Stop processing",
          timeoutType);
      return;
    }

    if (currentState.equals(TimeoutState.EXPIRED)) {
      logger.warn(
          "Received timeout message of type {}, but it has already expired. Stop processing",
          timeoutType);
      return;
    }

    switch (timeoutMessage.timeoutType()) {
      case PROPOSE:
        {
          /** Pseudocode Line 68, 69 */
          // TODO Kai: Why are we sending this without the request? (defined in the pseudocode)
          var depPropose = new DepProposeWithRequest(this.slot.getDepPropose(), null);
          var msg = new ISOSMessageWrapper(depPropose, this.ownReplicaId.value());

          this.msgSender.broadcastToReplicas(false, msg);
        }
        break;
      case COMMIT:
        {
          /** Pseudocode Line 70, 71 */
          this.moveSelfToNewView(ViewNumber.increaseViewNumber(this.slot.getViewNumber()));
        }
        break;
      case VIEWCHANGE:
        {
          // Pseudocode line 121, 122
          // Move to new view  v_{s_j}+1
          this.moveSelfToNewView(ViewNumber.increaseViewNumber(this.slot.getViewNumber()));
        }
        break;
      case VIEWCHANGE_COMMIT:
        {
          // is actually not its "own" timeout type, instead commit timeout with reduced duration
          this.moveSelfToNewView(ViewNumber.increaseViewNumber(this.slot.getViewNumber()));
        }
        break;
      case QUERY_EXEC:
        {
          // Pseudocode Line 136, 137
          // I assume that the QueryExec message is not broadcast to itself, because we ask for the
          // DepPropose message and the dependencies so that we can commit and forward it to the
          // execution.
          this.msgSender.broadcastToReplicas(
              false,
              new ISOSMessageWrapper(
                  new QueryExecMessage(this.seqNum, this.ownReplicaId), this.ownReplicaId));
        }
        break;
    }
    timeoutStates.put(
        timeoutType,
        TimeoutState.EXPIRED); // when set to expired, we know that it was already executed
  }

  /**
   * Cancels the timeout of the given timeoutType by setting the canceled flag. If cancelTimeout is
   * called before the scheduled timeout has started, the timeout never runs. If the timeout expires
   * while cancelTimeout runs, it will not run, because the timeout logic checks the flag before
   * running.
   *
   * @param timeoutType
   */
  private void cancelTimeout(ISOSTimeoutType timeoutType) throws IllegalStateException {
    this.timeoutStates.put(timeoutType, TimeoutState.CANCELED);
    logger.debug("Cancel timeout {}", timeoutType);

    var timeout = this.currentTimeouts.get(timeoutType);
    if (timeout == null) {
      logger.debug("Tried to cancel timeout of type {}, but doesn't exist", timeoutType);
      return;
    }
    timeout.cancel(false);
  }

  /**
   * Triggers the timeout logic if it hasn't happened yet.
   *
   * @param timeoutType
   * @return
   */
  private void triggerTimeoutExpiry(ISOSTimeoutType timeoutType) {
    // We have to cancel the timeout, if it hasn't run yet. If it already expired, we only have
    // double messages, which is fine.
    this.cancelTimeout(timeoutType);

    var timeout = this.currentTimeouts.get(timeoutType);
    if (timeout == null) {
      logger.info("Tried to timeout of type {}, but doesn't exist", timeoutType);
      return;
    }
    this.incomingQueue.addFirst(new TimeoutMessage(timeoutType, this.seqNum, this.ownReplicaId));
  }

  // endregion

  // region Preconditions, buffering message

  /**
   * Unified method that checks whether the step precondition holds for a message in the current
   * state. Generally, if a step precondition does not hold, the message should be buffered so that
   * it can be processed later when the correct step is reached.
   */
  private boolean stepPrecondHolds(ISOSMessageType msgType) {
    var currentStep = this.slot.getStep();
    return switch (msgType) {
      // Fast Path
      case DEP_PROPOSE_WITH_REQ -> currentStep == AgreementSlotPhase.INIT; // Throw away message
      case DEP_VERIFY -> currentStep == AgreementSlotPhase.PROPOSED; // Keep Message
      case DEP_COMMIT -> currentStep == AgreementSlotPhase.FP_VERIFIED; // Keep Message
      // Reconciliation Path
      case REC_PREPARE -> currentStep == AgreementSlotPhase.RP_VERIFIED; // Keep Message
      case REC_COMMIT -> currentStep == AgreementSlotPhase.RP_PREPARED; // Keep Message
      // View Change
      case VC_VIEWCHANGE -> currentStep == AgreementSlotPhase.VIEW_CHANGE; // Keep Message
      case VC_NEWVIEW -> currentStep == AgreementSlotPhase.VIEW_CHANGE; // Keep Message
      case VC_QUERYEXEC -> true;
      case VC_EXEC -> true;
      // Invalid cases
      case DEP_PROPOSE ->
          throw new IllegalArgumentException("DepPropose without Request cannot be processed");
      case TIMEOUT -> throw new IllegalArgumentException("Timeout message does not have precond");
      default -> throw new IllegalArgumentException(String.format("Invalid msgType %s", msgType));
    };
  }

  /**
   * Processes messages that have been buffered due to a step mismatch. After moving to a new step,
   * call this procedure to handle the messages.
   *
   * @param newStep The new step of the agreement slot after finishing the previous step
   */
  private void processBufferedMessages(AgreementSlotPhase newStep, ViewNumber currentView) {
    switch (newStep) {
      case NULL -> {}
      case INIT -> {}
      case PROPOSED -> { //
        getAndHandleMessagesFromBuffer(ISOSMessageType.DEP_VERIFY, null);
      }
      case FP_VERIFIED -> {
        getAndHandleMessagesFromBuffer(ISOSMessageType.DEP_COMMIT, null);
      }
      case FP_COMMITTED -> {
        // done
      }
      case RP_VERIFIED -> {
        getAndHandleMessagesFromBuffer(ISOSMessageType.REC_PREPARE, currentView);
      }
      case RP_PREPARED -> {
        getAndHandleMessagesFromBuffer(ISOSMessageType.REC_COMMIT, currentView);
      }
      case RP_COMMITTED -> {
        // done
      }
      case VIEW_CHANGE -> {
        getAndHandleMessagesFromBuffer(ISOSMessageType.VC_VIEWCHANGE, currentView);
        getAndHandleMessagesFromBuffer(ISOSMessageType.VC_NEWVIEW, currentView);
      }
    }
  }

  private void getAndHandleMessagesFromBuffer(ISOSMessageType msgType, ViewNumber currentView) {
    Collection<? extends ISOSMessage> bufferedMessages;
    if (currentView == null) {
      bufferedMessages = this.bufferedMessages.removeBufferedMsgWithoutView(msgType);
    } else {
      bufferedMessages = this.bufferedMessages.removeBufferedMsgWithView(msgType, currentView);
    }

    if (!bufferedMessages.isEmpty()) {
      logger.info(
          "Buffer contains {} messages of type {}. Process messages.",
          bufferedMessages.size(),
          msgType);
    }

    // Handle the messages sequentially
    for (ISOSMessage msg : bufferedMessages) {
      this.handleMessage(msg);
    }
  }

  // endregion

  // region Fast Path
  /**
   * Handles a depPropose message, which means that we are the follower for this agreement slot.
   * Pseudocode Line 20-35
   *
   * @param depProposeWithR
   */
  private void handleDepProposeWithRequest(DepProposeWithRequest depProposeWithR) {
    // Step precondition checked in separate method

    var depPropose = depProposeWithR.depPropose();
    var request = depProposeWithR.request();

    // Line 22: assert F is valid fast-path quorum
    // TODO Kai: what is a valid fast-path quorum?
    // Answer: Exactly 2f replicaIds, replicaIds exist,

    // Line 23: First propose from coordinator
    if (this.slot.getRequest() != null) {
      logger.warn("We have already received a DepPropose, skipping this message");
      return;
    }
    if (depPropose.seqNum().replicaId() != depPropose.coordinatorId().value()) {
      logger.error(
          "ReplicaId mismatch between sequence number and coordinatorId in depPropose message");
      return;
    }

    // Line 24: Wait for dependencies, and previous slot from coordinator co
    var waitDeps = new HashSet<>(depPropose.depSet().dependencies());

    // If we are the first sequence number, we do not have to wait for the previous one
    if (depPropose.seqNum().sequenceCounter() > 0) {
      var prevSlot = SequenceNumber.prevSequenceNumber(depPropose.seqNum());
      waitDeps.add(prevSlot); // wait for D ∪ s_{j−1}
    }

    // TODO Kai: do we have to wait in a loop with condition check?
    try {
      this.dependencyWait.waitUntilConsensusStarted(waitDeps);
      // TODO Kai: Expiring timeouts should cancel this waiting as well (?)
    } catch (InterruptedException e) {
      logger.error(
          "Interrupted while waiting for dependencies {}. Stop processing depPropose.", waitDeps);
      Thread.currentThread().interrupt();
      return;
    }

    // End of preconditions, assert, and wait

    // Line 25
    // Propose timeout is only created by followers, not coordinators
    if (this.slot.getDepPropose() == null) {
      this.startTimeout(ISOSTimeoutType.COMMIT);
      this.startTimeout(ISOSTimeoutType.PROPOSE);
    }

    this.slot.setDepPropose(depPropose);

    // Line 29
    if (request != null) {
      var depSet = this.conflictChecker.getCompactDependencySet(this.seqNum, request);
      this.slot.setRequest(request);
      this.conflictChecker.addClientRequest(this.seqNum, request, depSet);
      this.slot.setStep(AgreementSlotPhase.PROPOSED);
      // if we are in the Follower quorum, send a DepPropose message
      if (depPropose.followerQuorum().contains(this.ownReplicaId)) {
        var depVerify =
            new DepVerifyMessage(
                this.slot.getSeqNum(), this.ownReplicaId, depPropose.calculateHash(), depSet);

        var wrapper = new ISOSMessageWrapper(depVerify, this.ownReplicaId.value());
        this.msgSender.broadcastToReplicas(true, wrapper);
      }

      // New step -> handle DepVerify that were buffered
      this.processBufferedMessages(this.slot.getStep(), this.slot.getViewNumber());
    } else {
      logger.error("Received DepPropose message without request! Is this valid?");
    }
  }

  private void handleDepVerify(DepVerifyMessage depVerify) {
    // Line 53: if we receive DepVerify from f+1 replicas, start commit timeout
    // Note: In this case, we assume that we have received f+1 *valid* DepVerify messages.
    // The pseudocode is ambiguous in this case, whether we should count DepVerify messages that
    // are not valid, e.g., due to a hash mismatch.
    if (this.slot.reachedDepVerifyQuorum(this.maxFaults + 1)) {
      // Start commit timeout if it wasn't started yet
      if (this.timeoutStates.get(ISOSTimeoutType.COMMIT) == TimeoutState.NULL) {
        this.startTimeout(ISOSTimeoutType.COMMIT);
      }
    }

    if (!slot.getDepPropose().calculateHash().equals(depVerify.depProposeHash())) {
      logger.error(
          "Hash mismatch with previous DepPropose and DepProposeHash of DepVerify message, throwing message away");
      return;
    }

    // Line 38: First verify from follower
    if (this.slot.getDepVerifys().containsKey(depVerify.followerId())) {
      logger.warn(
          "Already received DepVerify from follower {}, throwing message away",
          depVerify.followerId());
      return;
    }

    // Line 39: Follower is in fast-path quorum
    if (!this.slot.getDepPropose().followerQuorum().contains(depVerify.followerId())) {
      logger.warn(
          "Received DepVerify, but sender is not in Follower quorum, throwing message away");
      return;
    }

    try {
      // TODO Kai: While we are waiting, we cannot process any other messages. Is this fine? Maybe
      // start waiting for the union of dependencies after we have reached the quorum of
      // depVerifys?
      this.dependencyWait.waitUntilConsensusStarted(depVerify.depSet().dependencies());
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for dependencies of received DepVerify message.");
      return;
    }

    // Add received DepVerify
    this.slot.setDepVerify(depVerify.followerId(), depVerify);

    if (!this.slot.reachedDepVerifyQuorum(2 * this.maxFaults)) {
      logger.debug("Did not reach quorum of DepVerify messages yet in slot {}", this.seqNum);
      return;
    }

    // We have reached the quorum of messages
    this.cancelTimeout(ISOSTimeoutType.PROPOSE);

    // TODO Kai: do we have to check our own dependencySet as well?
    var fpVerified = this.slot.isFpVerified(this.maxFaults);
    if (!fpVerified) {
      // At least 1 dependency is not reported by at least f+1 followers
      // Enter reconciliation path, stop participating in fast path
      logger.info(
          "At least 1 dependency is not reported by at least f+1 followers. Enter reconciliation path.");
      var depVerifyHash = this.slot.getDepVerifyHashCached();
      enterReconciliationPath(depVerifyHash);
      return;
    }

    // We are fp-Verified

    this.slot.setStep(AgreementSlotPhase.FP_VERIFIED);

    // Here, h(dv) refers to the set of DepVerifys received from the followers in F.
    var depVerifyHash = this.slot.getDepVerifyHashCached();
    var depCommitMsg = new DepCommitMessage(this.seqNum, this.ownReplicaId, depVerifyHash);

    var wrapper = new ISOSMessageWrapper(depCommitMsg, this.ownReplicaId.value());

    // Page 4 ISOS: agreement slot is fp-committed once a replica has obtained matching DepCommits
    // from
    // 2f+1 replicas (possibly including itself)
    this.msgSender.broadcastToReplicas(true, wrapper);

    // New step -> handle DepCommit that were buffered
    this.processBufferedMessages(this.slot.getStep(), this.slot.getViewNumber());
  }

  private void handleDepCommit(DepCommitMessage depCommit) {
    this.bufferedMessages.storeDepCommit(depCommit);

    if (!this.bufferedMessages.depCommitQuorumSizeReached((2 * this.maxFaults) + 1)) {
      return;
    }

    // We have received at least 2f+1 messages
    // Now we need to check whether our depVerifyHash matches with the hashes

    // Precondition 2: The hash of *our* vector containing the DepVerify messages from the F quorum
    // has to match with the hash from the received DepCommit messages as well
    if (!this.bufferedMessages.depCommitQuorumWithSameHashReached(
        this.slot.getDepVerifyHashCached(), (2 * this.maxFaults) + 1)) {
      logger.info("Commit Quorum with same DepVerifyHash not reached yet");
      return;
    }

    // Our DepVerify hash matches with a quorum of DepVerifyHashes of received commit messages
    this.cancelTimeout(ISOSTimeoutType.PROPOSE);
    this.cancelTimeout(ISOSTimeoutType.COMMIT);

    // Forward the slot to execution
    // Dependency set used in execution is union set of all dependencies of the follower quorum
    // defined initially by the DepPropose
    // TODO Kai: do we have to include depPropose dependencies here?
    var depVerifys = this.slot.getDepVerifys().values().stream().toList();
    var unionDepsFollowerQuorum = DepVerifyMessage.unionOfDependencies(depVerifys, null);
    var executeMsg =
        new CommittedCommand(this.seqNum, this.slot.getRequest(), unionDepsFollowerQuorum);
    this.slot.setExec(executeMsg);
    this.conflictChecker.updateCommitedRequest(executeMsg);
    this.requestExecutor.forwardRequestToExecution(executeMsg);
  }

  // endregion

  // region Reconciliation Path
  /** Pseudocode line 72-75 */
  private void enterReconciliationPath(String depVerifysFollowerQuorumHash) {
    this.slot.setStep(AgreementSlotPhase.RP_VERIFIED);
    var prepareMsg =
        new PrepareMessage(
            this.seqNum,
            this.slot.getViewNumber(),
            this.ownReplicaId,
            depVerifysFollowerQuorumHash);
    var wrapper = new ISOSMessageWrapper(prepareMsg, this.ownReplicaId);
    // We have to process our own prepare message as well, so includeSelf is true
    this.msgSender.broadcastToReplicas(true, wrapper);

    // New step -> handle Prepare messages that were buffered
    this.processBufferedMessages(this.slot.getStep(), this.slot.getViewNumber());
  }

  /**
   * The reconciliation path only works if 2f+1 replicas have received the same DepVerify messages.
   * However, the dependencies from the received DepVerify messages diverge in such a way that the
   * condition that only a single proposal can complete for a slot cannot be guaranteed. Thus, the
   * replicas have to agree to a single dependency set with a 2-phase-commit-like communication
   * pattern.
   *
   * @param prepare
   */
  private void handlePrepareMessage(PrepareMessage prepare) {
    // a correct replica that has reached fp-verified does not contribute to the reconciliation
    // path.

    // We have to check our DepVerify hash as well
    // Set of previously received DepVerifys
    String depVerifyHash = this.slot.getDepVerifyHashCached();

    if (!depVerifyHash.equals(prepare.depVerifysHash())) {
      logger.warn("Hash mismatch with received prepare message, throwing message away");
      return;
    }

    // Save to prepare quorum
    this.bufferedMessages.storePrepare(prepare);

    // Before we can continue processing, we need to fulfill the preconditions

    if (!this.slot.getViewNumber().equals(prepare.viewNumber())) {
      logger.info(
          "View number mismatch in prepare, own view number: {}, received: {}",
          this.slot.getViewNumber(),
          prepare.viewNumber());
      return;
    }

    // If we have a 2f+1 quorum, we can continue
    // TODO Kai: do we need to check for other conditions of the quorum? Same hash?
    if (!this.bufferedMessages.prepareQuorumSizeReached(
        this.slot.getViewNumber(), (2 * this.maxFaults) + 1)) {
      logger.info(
          "Received prepare message for view {}, but quorum not reached yet.",
          this.slot.getViewNumber());
      return;
    }

    this.slot.setStep(AgreementSlotPhase.RP_PREPARED);
    var commitMsg =
        new CommitMessage(this.seqNum, this.slot.getViewNumber(), this.ownReplicaId, depVerifyHash);
    var wrapper = new ISOSMessageWrapper(commitMsg, this.ownReplicaId);
    this.msgSender.broadcastToReplicas(true, wrapper);

    // New step -> handle (Reconciliation) Commit messages that were buffered
    this.processBufferedMessages(this.slot.getStep(), this.slot.getViewNumber());
  }

  /**
   * Preconditions are similar to {@link #handlePrepareMessage(PrepareMessage)}.
   *
   * @param commit
   */
  private void handleCommitMessage(CommitMessage commit) {
    var depVerifys = this.slot.getDepVerifys().values().stream().toList();
    String depVerifyHash = this.slot.getDepVerifyHashCached();

    if (!depVerifyHash.equals(commit.depVerifysHash())) {
      logger.warn("Hash mismatch with received commit message, throwing message away");
      return;
    }

    // save to quorum
    this.bufferedMessages.storeCommit(commit);

    // Before we can continue processing, we need to fulfill the preconditions
    if (!this.slot.getViewNumber().equals(commit.viewNumber())) {
      logger.info(
          "View number mismatch in commit, own view number: {}, received: {}",
          this.slot.getViewNumber(),
          commit.viewNumber());
      return;
    }

    if (!this.bufferedMessages.commitQuorumSizeReached(
        this.slot.getViewNumber(), (2 * this.maxFaults) + 1)) {
      logger.info(
          "Received commit message for view {}, but quorum not reached yet.",
          this.slot.getViewNumber());
      return;
    }

    logger.info("We have reached 2f+1 RpCommit messages for view {}!", this.slot.getViewNumber());

    this.slot.setStep(AgreementSlotPhase.RP_COMMITTED);
    // Line 83
    this.cancelTimeout(ISOSTimeoutType.COMMIT);

    // ISOS Paper: ...together with the union of the dependency sets of all DepVerifys and the
    // associated DepPropose.
    // Line 85
    var unionDepsFollowerQuorum =
        DepVerifyMessage.unionOfDependencies(depVerifys, this.slot.getDepPropose());
    var executeMsg =
        new CommittedCommand(this.seqNum, this.slot.getRequest(), unionDepsFollowerQuorum);
    this.slot.setExec(executeMsg);
    this.conflictChecker.updateCommitedRequest(executeMsg);
    this.requestExecutor.forwardRequestToExecution(executeMsg);
  }

  // endregion

  // region View Change

  /**
   * Upon move to new view for slot. This function is called when a timeout expires, or when enough
   * ViewChange messages are received.
   *
   * <p>Pseudocode line 86-100
   */
  private void moveSelfToNewView(ViewNumber newViewNum) {
    // Paper: Once a replica decides to abort a view, the replica stops to process requests for the
    // old view and broadcasts a ViewChange message for the new view.

    // Move to new view v_s_j+1
    var previousViewNum = this.slot.getViewNumber();
    logger.info("Move from view {} to new view {}", previousViewNum, newViewNum);

    // If propose timeout is active, trigger its expiry (i.e., timeout logic should be executed now)
    this.triggerTimeoutExpiry(ISOSTimeoutType.PROPOSE);

    this.cancelTimeout(ISOSTimeoutType.COMMIT);
    this.cancelTimeout(ISOSTimeoutType.VIEWCHANGE);

    DepProposeWithRequest dp =
        new DepProposeWithRequest(this.slot.getDepPropose(), this.slot.getRequest());
    List<DepVerifyMessage> dv = this.slot.getDepVerifys().values().stream().toList();

    // has to be 2f matching DepVerifys in both cases
    // ->
    var currentStep = this.slot.getStep();

    // Pseudocode line 91-95
    // Fast path Certificate
    if (currentStep.equals(AgreementSlotPhase.FP_VERIFIED)
        || currentStep.equals(AgreementSlotPhase.FP_COMMITTED)) {
      this.slot.setViewChangeCertificate(
          new FastPathCertificate(dp, dv /*ViewNumber of FPC is constant -1*/));

      // Sanity check, is not necessary
      if (!this.slot.isFpVerified(this.maxFaults)) {
        throw new RuntimeException(
            String.format("Step is %s, but fp-verified predicate is not fulfilled", currentStep));
      }
    }
    // Reconciliation Path Certificate
    else if (currentStep.equals(AgreementSlotPhase.RP_PREPARED)
        || currentStep.equals(AgreementSlotPhase.RP_COMMITTED)) {

      // Line 94: Set of 2f+1 Prepares with h(dv)
      // h(dv) is already checked when adding the prepare
      List<PrepareMessage> prep =
          this.bufferedMessages.getPrepares(previousViewNum).stream().toList();

      /**
       * Are we sending the new view number, or the previous one?
       *
       * <p>Pseudocode says to send the view number that is stored in the view variable, before it
       * is updated with the new view number -> older one
       *
       * <p>However, the new view number would make more sense intuitively, because we are currently
       * in the moveToNewView step
       *
       * <p>Looking at the FPC as well where -1 is used in the certificate, we are using the
       * messages that we received in the *previous* view for the certificate. When the fast path is
       * successful, a view change is not necessary, so the view number is always -1. Thus, we use
       * the previousViewNum in the certificate.
       */
      this.slot.setViewChangeCertificate(
          new ReconciliationPathCertificate(dp, dv, prep, previousViewNum));
    }

    // After creating the certificate (if the conditions are fulfilled), update the view number
    // Pseudocode line 96-97
    this.slot.setViewNumber(newViewNum);
    this.slot.setPeerViewNumber(this.ownReplicaId, newViewNum);
    this.slot.setStep(AgreementSlotPhase.VIEW_CHANGE);
    this.startTimeout(ISOSTimeoutType.QUERY_EXEC);
    // TODO Kai: Do we need to broadcast to ourselves or not?
    this.msgSender.broadcastToReplicas(
        false,
        new ISOSMessageWrapper(
            new ViewChangeMessage(
                this.seqNum, newViewNum, this.ownReplicaId, this.slot.getViewChangeCertificate()),
            this.ownReplicaId));

    // New step -> handle ViewChange / NewView messages that were buffered
    this.processBufferedMessages(this.slot.getStep(), this.slot.getViewNumber());
  }

  /**
   * This function determines the coordinator for a given view and sequence number.
   *
   * @param originalCoordinatorId $s_j.co$: (original) Coordinator Id of the agreement slot
   * @param currentViewNumber: $v_{s_j}$: View number of the new view
   * @param replicaCount: $N$: current count of replicas, used for m
   */
  private static int getNextViewCoordinator(
      ReplicaId originalCoordinatorId, ViewNumber currentViewNumber, int replicaCount) {
    return (originalCoordinatorId.value() + Math.max(0, currentViewNumber.value())) % replicaCount;
  }

  /**
   * Pseudocode line 101-107 (Receiving single ViewChange message), 109-116 (2f+1 Quorum for
   * View-Change coordinator), 117-120 (2f+1 quorum for normal replica)
   *
   * @param viewChange
   */
  private void handleViewChangeMessage(ViewChangeMessage viewChange) {
    // region General replica
    var currentPeerViewNumber = this.slot.getPeerViewNumber(viewChange.replicaId());
    if (viewChange.viewNumber().compareTo(currentPeerViewNumber) <= 0) {
      // if view number of message is smaller than currently stored view number
      logger.error(
          "View number of received ViewChange message {} is smaller or equal than currently known view number {} of replica {}, throwing message away",
          viewChange.viewNumber(),
          currentPeerViewNumber,
          viewChange.replicaId());
      return;
    }

    // end precondition

    // Line 103
    this.slot.setPeerViewNumber(viewChange.replicaId(), viewChange.viewNumber());

    var highestQuorumViewNumber = this.slot.getHighestViewNumberByQuorum(this.maxFaults + 1);

    // Line 106
    if (highestQuorumViewNumber.isPresent()
        && highestQuorumViewNumber.get().compareTo(this.slot.getViewNumber()) > 0) {
      // Line 107: if the highest view number of others is larger than our own, move to that view
      // number
      this.moveSelfToNewView(highestQuorumViewNumber.get());
    }
    // endregion

    // -> After handling the viewChange logic when receiving a single viewchange message, we store
    // the ViewChange messages in a helper structure to determine whether we have a ViewChange
    // message
    // quorum for the current view
    this.slot.setViewChange(viewChange);

    // The next steps, regardless of whether we are the ViewChange coordinator or just a replica,
    // are
    // only executed if a 2f+1 quorum of ViewChanges is reached with ViewChange messages that are in
    // the same view as us currently.

    if (!this.slot.reachedViewChangeQuorum(this.slot.getViewNumber(), 2 * maxFaults + 1)) {
      logger.info(
          "We have not reached a quorum of ViewChange messages for the view {} yet, stop processing",
          this.slot.getViewNumber());
      return;
    }

    // We have reached a quorum of 2f+1 ViewChange messages! Now, depending on whether we are the
    // view-change coordinator of the new view, we have different logic (Pseudocode Line 109-116 for
    // coordinator, Line 117-120 for normal replica)

    var nextCoordinator =
        AgmtSlotQueueProcessor.getNextViewCoordinator(
            this.slot.getDepPropose().coordinatorId(), this.slot.getViewNumber(), replicaCount);

    if (this.ownReplicaId.value() == nextCoordinator) { // We are the coordinator.
      // Line 111
      // This variable is called "VCS" in pseudocode
      var viewChangeSet = this.slot.getViewChanges(this.slot.getViewNumber());

      // Assert all view changes are valid, and sort the ViewChanges based on their certificate

      var vcsValid =
          viewChangeSet.entrySet().stream()
              .allMatch(
                  (entry) -> {
                    var msg = entry.getValue();
                    if (msg.certificate().certificateType().equals(CertificateType.NULL)) {
                      return true;
                    } else if (msg.certificate() instanceof FastPathCertificate fpc) {
                      if (fpc.previousViewNumber().compareTo(msg.viewNumber()) <= 0) {
                        return true;
                      }
                      logger.error(
                          "FPC of Replica {} is invalid due to its viewNumber {} being larger than the view number {} of the containing message",
                          entry.getKey(),
                          fpc.previousViewNumber(),
                          msg.viewNumber());
                    } else if (msg.certificate() instanceof ReconciliationPathCertificate rpc) {
                      if (rpc.previousViewNumber().compareTo(msg.viewNumber()) <= 0) {
                        return true;
                      }
                      logger.error(
                          "RPC of Replica {} is invalid due to its viewNumber {} being larger than the view number {} of the containing message",
                          entry.getKey(),
                          rpc.previousViewNumber(),
                          msg.viewNumber());
                    }

                    return false;
                  });

      if (!vcsValid) {
        logger.error("View Change Set is invalid, do not broadcast NewView message");
        return;
      }

      // Line 112-115
      // Certificate Priority (highest->lowest): RPC -> FPC -> null
      Optional<DepProposeAndDepVerifys> dpdv =
          AgmtSlotQueueProcessor.pickDepProposeAndDepVerifys(viewChangeSet.values());
      DepProposeWithRequest dp = dpdv.map(DepProposeAndDepVerifys::depPropose).orElse(null);
      List<DepVerifyMessage> vecDv = dpdv.map(DepProposeAndDepVerifys::depVerifys).orElse(null);

      this.msgSender.broadcastToReplicas(
          true,
          new ISOSMessageWrapper(
              new NewViewMessage(
                  this.seqNum,
                  this.slot.getViewNumber(),
                  this.ownReplicaId,
                  dp,
                  vecDv,
                  Set.copyOf(viewChangeSet.values())),
              this.ownReplicaId));

    } else { // We are not the coordinator.
      // TODO Kai: Does the view-change coordinator execute this logic as well?
      // Line 117-120
      // Precondition in Line 118 already checked above

      this.startTimeout(ISOSTimeoutType.VIEWCHANGE);
      this.cancelTimeout(ISOSTimeoutType.QUERY_EXEC);
    }
  }

  /**
   * Line 112-115
   *
   * <p>Certificate Priority (highest->lowest): RPC -> FPC -> null
   *
   * @param viewChanges
   * @return
   */
  public static Optional<DepProposeAndDepVerifys> pickDepProposeAndDepVerifys(
      Collection<ViewChangeMessage> viewChanges) {

    Map<CertificateType, List<ViewChangeMessage>> msgsByCertificateType =
        viewChanges.stream()
            .collect(Collectors.groupingBy(msg -> msg.certificate().certificateType()));

    if (msgsByCertificateType.containsKey(CertificateType.RPC)
        && !msgsByCertificateType.get(CertificateType.RPC).isEmpty()) {
      List<ViewChangeMessage> rpcMsgs = msgsByCertificateType.get(CertificateType.RPC);

      // Pseudocode: Reconciliation-path result for highest view if RPC certificate exists
      ReconciliationPathCertificate highestViewRpc =
          (ReconciliationPathCertificate)
              rpcMsgs.stream()
                  .max(Comparator.comparing(ViewChangeMessage::viewNumber))
                  .map(ViewChangeMessage::certificate)
                  .get();

      return Optional.of(
          new DepProposeAndDepVerifys(
              highestViewRpc.originalDepPropose(),
              DepVerifyMessage.sortDepVerifys(highestViewRpc.depVerifyMessages())));
    } else if (msgsByCertificateType.containsKey(CertificateType.FPC)
        && !msgsByCertificateType.get(CertificateType.FPC).isEmpty()) {
      // Fast-Path result from any
      // Cast is always correct as fpcReplicas is only added if certificate is instance of
      // FastPathCertificate
      List<ViewChangeMessage> fpcMsgs = msgsByCertificateType.get(CertificateType.FPC);
      FastPathCertificate fpc = (FastPathCertificate) fpcMsgs.getFirst().certificate();
      return Optional.of(
          new DepProposeAndDepVerifys(
              fpc.originalDepPropose(), DepVerifyMessage.sortDepVerifys(fpc.depVerifyMessages())));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Broadcasted by the View-change coordinator.
   *
   * <p>Pseudocode line 123-135
   *
   * @param newView
   */
  private void handleNewViewMessage(NewViewMessage newView) {

    if (this.slot.getViewNumber() != newView.viewNumber()) {
      logger.warn(
          "View mismatch for current slot {} and received NewView message {}, buffering for later viewchange",
          this.slot.getViewNumber(),
          newView.viewNumber());
      this.bufferedMessages.bufferMessage(newView);
      return;
    }

    // Start asserts
    if (getNextViewCoordinator(
            this.slot.getDepPropose().coordinatorId(), this.slot.getViewNumber(), replicaCount)
        != newView.coordinatorId().value()) {
      logger.error(
          "Received NewView message from {}, but doesn't match with determined ViewCoordinator by original DepProposer {}, current view number {}, and replica count {}",
          newView.coordinatorId(),
          this.slot.getDepPropose().coordinatorId(),
          this.slot.getViewNumber(),
          replicaCount);
      return;
    }

    // TODO Kai: What is a valid View-Change message?

    // Assert dp, dv are correctly picked based on View Change Set
    var depProposeAndDepVerifys =
        AgmtSlotQueueProcessor.pickDepProposeAndDepVerifys(newView.viewChanges());
    if (depProposeAndDepVerifys.isEmpty()
        && (newView.depPropose() != null || newView.depVerifys() != null)) {
      logger.error(
          "Own determined FPC/RPC is null, but NewView message depPropose and/or depVerifys are not null. Stop processing");
      return;

    } else if (!depProposeAndDepVerifys.get().depPropose().equals(newView.depPropose())
        // We are able to equal the two lists, because they are sorted before being returned by the
        // pick method
        || !depProposeAndDepVerifys.get().depVerifys().equals(newView.depVerifys())) {
      logger.error(
          "DepPropose and/or DepVerifys determined by current replica differ from received ones in NewView message. Stop processing");
      return;
    }
    // End asserts

    var dp = newView.depPropose();
    var vecDv = newView.depVerifys();

    this.slot.setDepPropose(dp.depPropose());
    this.slot.setRequest(dp.request());
    this.conflictChecker.overwriteClientRequest(this.seqNum, dp.request());
    // Line 129: Cleanup DepVerifys
    this.slot.replaceDepVerifys(vecDv);

    if (newView.seqNum().replicaId() == ownReplicaId.value() && dp.depPropose() == null) {
      // TODO Kai: what is permute-fast-quorum() from pseudocode?
      // permute-fast-quorum() (?)
      // Re-propose request in a new slot (?)
      // But if dp is null, where do we get the request from?
    }
    this.startTimeout(ISOSTimeoutType.VIEWCHANGE); // same as commit timeout, but with reduced time
    // Line 135: Enter reconciliation path
    enterReconciliationPath(this.slot.getDepVerifyHashCached());
  }

  private void handleQueryExecMessage(QueryExecMessage queryExec) {
    if (this.slot.getExec() == null) {
      logger.info(
          "Received QueryExec, but did not forward request to execution yet. Throwing message away");
      return;
    }

    OrderedClientRequest dp = this.slot.getExec().clientRequest();
    DependencySet D = this.slot.getExec().depSet();

    ReplicaId[] receivers = new ReplicaId[] {queryExec.logicalSender()};
    this.msgSender.sendToReplicas(
        receivers,
        new ISOSMessageWrapper(
            new ExecMessage(this.seqNum, this.ownReplicaId, dp, D), this.ownReplicaId));
  }

  private void handleExecMessage(ExecMessage exec) {
    // We have to reach a f+1 quorum
    if (this.slot.getExec() == null) {
      logger.info(
          "Received Exec, but did already forward message to execution. Throwing message away");
      return;
    }

    this.bufferedMessages.storeExec(exec);

    if (!this.bufferedMessages.execQuorumWithSameContentsReached(
        exec.clientRequest(), exec.dependencySet(), this.maxFaults + 1)) {
      logger.info("Did not reach f+1 quorum for exec messages yet.");
      return;
    }

    // We have reached quorum
    var executeMsg = new CommittedCommand(this.seqNum, exec.clientRequest(), exec.dependencySet());
    this.slot.setExec(executeMsg);
    this.conflictChecker.updateCommitedRequest(executeMsg);
    this.requestExecutor.forwardRequestToExecution(executeMsg);
  }

  // endregion


}
