package isos.consensus;

import isos.communication.MessageSender;
import isos.consensus.buffer.ISOSMessageBuffer;
import isos.consensus.model.*;
import isos.consensus.model.viewchange.FastPathCertificate;
import isos.consensus.model.viewchange.ReconciliationPathCertificate;
import isos.execution.ExecutableRequestReceiver;
import isos.execution.ExecuteMessage;
import isos.execution.graph.RequestConflictChecker;
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
import isos.message.replica.viewchange.NewViewMessage;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles incoming messages and delegates them to subtasks, depending on the current
 * state. In other words, it contains and executes the (core) business logic of ISOS. Additionally,
 * it handles timeouts and their logic if they expire.
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

  private final Lock slotLock;

  /**
   * When the DepPropose, DepVerify, or ViewChange messages change, this condition has to be
   * notified
   */
  private final Condition messageCountCondition;

  // --- Outside Dependencies ---
  /** Callback function to broadcast messages to replicas */
  private final MessageSender msgSender;

  /**
   * The conflictChecker returns the conflicts of a ClientRequest to all other ClientRequests that
   * we have already received.
   *
   * <p>Passed from the outside due to access to other agreement slots.
   */
  private final RequestConflictChecker conflictChecker;

  /** Passed from the outside due to access to other agreement slots. */
  private final DependencyWaitFunction dependencyWait;

  /**
   * Passed from the outside because request execution is not the responsibility of the
   * AgreementQueueProcessor
   */
  private final ExecutableRequestReceiver requestExecutor;

  private final int maxFaults;

  public AgmtSlotQueueProcessor(
      ReplicaId ownReplicaId,
      SequenceNumber seqNum,
      BlockingDeque<ISOSMessage> incomingQueue,
      TimeoutConfiguration timeoutConfig,
      MessageSender msgSender,
      AgreementSlot slot,
      RequestConflictChecker conflictChecker,
      DependencyWaitFunction dependencyWait,
      ExecutableRequestReceiver requestExecutor,
      int maxFaults) {
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
    this.slotLock = new ReentrantLock();
    this.messageCountCondition = this.slotLock.newCondition();

    // Outside Dependencies
    this.msgSender = msgSender;
    this.conflictChecker = conflictChecker;
    this.dependencyWait = dependencyWait;
    this.requestExecutor = requestExecutor;

    this.maxFaults = maxFaults;

    this.logger = LoggerFactory.getLogger(String.format("QueueProcessor %s", seqNum.toString()));
  }

  /**
   * Pseudocode line 60
   *
   * @param maxFaults
   * @throws InterruptedException
   */
  public void awaitWaitConditionCompleted(int maxFaults) throws InterruptedException {
    if (maxFaults < 0) {
      throw new IllegalArgumentException("Maximum faults is smaller than 0");
    } else if (maxFaults == 0) {
      logger.warn("Maximum faults is 0. Is ISOS configured correctly?");
    }
    int quorumSize = maxFaults + 1;

    this.slotLock.lock();
    try {
      while (slot.getDepPropose() == null // received valid DepPropose
          && slot.getDepVerifies().size() < quorumSize // received f+1 correctly signed DepVerifys
          && slot.getViewChanges().size() < quorumSize // received f+1 correctly signed ViewChanges
      ) {
        // if depPropose, depVerify, or viewChanges get changed, the condition should be
        // notified
        this.messageCountCondition.await();
      }
    } finally {
      this.slotLock.unlock();
    }
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
    logger.info(
        "Queue received message of type {} from sender {}", msg.msgType(), msg.logicalSender());

    // Use different handlers depending on the received message
    switch (msg) {
      case TimeoutMessage timeoutMessage -> this.handleTimeoutMessage(timeoutMessage);

      // Fast path
      case DepProposeWithRequest depPropose ->
          // This case only happens if we receive a depPropose from another replica. For requests
          // where
          // the current replica acts as the coordinator, see handleReceivedClientRequest().
          this.handleReceivedDepProposeWithRequest(depPropose);
      case DepProposeMessage ignored ->
          logger.error("Received DepPropose message without request, throwing away");
      case DepVerifyMessage depVerify -> this.handleReceivedDepVerify(depVerify);
      case DepCommitMessage depCommit -> this.handleReceivedDepCommit(depCommit);

      // Reconciliation path
      case PrepareMessage prepare -> this.handleReceivedPrepareMessage(prepare);
      case CommitMessage commit -> this.handleReceivedCommitMessage(commit);

      // View change
      case NewViewMessage newView -> this.handleReceivedNewViewMessage(newView);
      case ViewChangeMessage viewChange -> this.handleReceivedViewChangeMessage(viewChange);
      default -> {}
    }
  }

  /**
   * Called when our replica receives a request from a client and has to act as the coordinator for
   * that request.
   *
   * <p>Pseudocode Line 10-19
   */
  private void handleReceivedClientRequest() {
    // We can only be coordinator of a client request if the generated SequenceNumber is our own
    assert Objects.equals(this.ownReplicaId, this.seqNum.replicaIdRec());
    var r = slot.getRequest();

    DependencySet depSet = this.conflictChecker.conflicts(r);
    Set<ReplicaId> followerSet = msgSender.getLowestPingReplicas(2 * this.maxFaults);
    DepProposeMessage propose =
        new DepProposeMessage(seqNum, ownReplicaId, r.calculateHash(), depSet, followerSet);

    DepProposeWithRequest dp =
        new DepProposeWithRequest(propose, r); // Create wrapper message that includes the request

    this.slotLock.lock();
    try {
      this.slot.setDepPropose(propose);
      this.slot.setRequest(r);
      this.slot.setStep(AgreementSlotPhase.PROPOSED);
      // we have changed the messages in the slot, so signal all waiting threads

      this.messageCountCondition.signalAll();
    } finally {
      this.slotLock.unlock();
    }

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
    logger.info("Handle {} timeout", timeoutMessage.timeoutType());
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
          this.moveToNewView();
        }
        break;
      case VIEWCHANGE:
        {
          // Pseudocode line 121, 122
          // Move to new view  v_{s_j}+1
          this.moveToNewView();
        }
        break;
      case VIEWCHANGE_COMMIT:
        {
          // is actually not its "own" timeout type, instead commit timeout with reduced duration
          this.moveToNewView();
        }
        break;
      case QUERY_EXEC:
        {
          // TODO Kai: broadcast to self or not?
          //        this.msgSender.broadcastToReplicas(true, new QueryExecMessage());
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
    logger.info("Cancel timeout {}", timeoutType);

    var timeout = this.currentTimeouts.get(timeoutType);
    if (timeout == null) {
      logger.warn("Tried to cancel timeout of type {}, but doesn't exist", timeoutType);
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

  private boolean checkStepPrecond(ISOSMessageType msgType, AgreementSlotPhase currentStep) {
    boolean isCorrectStep = false;
    switch (msgType) {
      case DEP_PROPOSE -> {}
      case DEP_PROPOSE_WITH_REQ -> isCorrectStep = currentStep == AgreementSlotPhase.INIT;
      case DEP_VERIFY -> isCorrectStep = currentStep == AgreementSlotPhase.PROPOSED;
      case DEP_COMMIT -> {}
      case REC_PREPARE -> {}
      case REC_COMMIT -> {}
      case VC_VIEWCHANGE -> {}
      case VC_NEWVIEW -> {}
      case TIMEOUT -> {}
      default -> {
        logger.warn("Passed invalid msgType {}", msgType);
        return false;
      }
    }

    if (!isCorrectStep) {
      logger.warn("Step mismatch when processing {}, current step {}", msgType, currentStep);
    }
    return isCorrectStep;
  }

  // endregion

  // region Fast Path
  /**
   * Handles a depPropose message, which means that we are the follower for this agreement slot.
   * Pseudocode Line 20-35
   *
   * @param depProposeWithR
   */
  private void handleReceivedDepProposeWithRequest(DepProposeWithRequest depProposeWithR) {
    // Line 21: pre: step == init
    if (!this.checkStepPrecond(ISOSMessageType.DEP_PROPOSE_WITH_REQ, this.slot.getStep())) {
      return;
    }

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
      this.slotLock.lock();
      try {
        this.slot.setDepPropose(depPropose);
        this.messageCountCondition.signalAll();
      } finally {
        this.slotLock.unlock();
      }
    }

    // Line 29
    if (request != null) {
      // TODO Kai: assert r correctly signed
      var dependencies = this.conflictChecker.conflicts(request);
      this.slotLock.lock();
      try {
        this.slot.setRequest(request);
        this.slot.setStep(AgreementSlotPhase.PROPOSED);
        this.messageCountCondition.signalAll();
      } finally {
        this.slotLock.unlock();
      }
      // if we are in the Follower quorum, send a DepPropose message
      if (depPropose.followerQuorum().contains(this.ownReplicaId)) {
        var depVerify =
            new DepVerifyMessage(
                this.slot.getSeqNum(), this.ownReplicaId, depPropose.calculateHash(), dependencies);

        var wrapper = new ISOSMessageWrapper(depVerify, this.ownReplicaId.value());
        this.msgSender.broadcastToReplicas(true, wrapper);
      }
    }
  }

  private void handleReceivedDepVerify(DepVerifyMessage depVerify) {
    // FIXME Kai: Add pseudocode line 53 / 54
    // FIXME: if we receive DepVerify from f+1 replicas, we have to start commit timeout
    // (disregarding fast path quorum, hash, and dependencies)

    if (!this.checkStepPrecond(ISOSMessageType.DEP_VERIFY, this.slot.getStep())) {
      this.bufferedMessages.bufferMessage(depVerify);
      return;
    }

    if (!slot.getDepPropose().calculateHash().equals(depVerify.depProposeHash())) {
      logger.error(
          "Hash mismatch with previous DepPropose and DepProposeHash of DepVerify message, throwing message away");
      return;
    }

    // Line 38: First verify from follower
    if (this.slot.getDepVerifies().containsKey(depVerify.followerId())) {
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
      // depVerifies?
      this.dependencyWait.waitUntilConsensusStarted(depVerify.depSet().dependencies());
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for dependencies of received DepVerify message.");
      return;
    }

    // Add received DepVerify
    this.slot.setDepVerify(depVerify.followerId(), depVerify);

    if (!this.slot.reachedDepVerifyQuorum(2 * this.maxFaults)) {
      logger.info("Did not reach quorum of DepVerify messages yet.");
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
  }

  private void handleReceivedDepCommit(DepCommitMessage depCommit) {
    this.bufferedMessages.storeDepCommit(depCommit);

    if (!this.bufferedMessages.depCommitQuorumReached((2 * this.maxFaults) + 1)) {
      return;
    }

    // We have received at least 2f+1 messages
    // Now we need to check whether our depVerifyHash matches with the hashes

    // Preconditions
    // Precondition 1
    if (this.slot.getStep() != AgreementSlotPhase.FP_VERIFIED) {
      logger.error("Step mismatch for DepCommit, returning early");
      return;
    }

    // Precondition 2: The hash of *our* vector containing the DepVerify messages from the F quorum
    // has to match with the hash from the received DepCommit messages as well
    if (!this.bufferedMessages.depCommitQuorumWithSameHashReached(
        this.slot.getDepVerifyHashCached(), (2 * this.maxFaults) + 1)) {
      logger.info("Commit Quorum with same DepVerifyHash not reached yet");
      return;
    }

    // Preconditions done

    // Our DepVerify hash matches with a quorum of DepVerifyHashes of received commit messages
    this.cancelTimeout(ISOSTimeoutType.PROPOSE);
    this.cancelTimeout(ISOSTimeoutType.COMMIT);

    // Forward the slot to execution
    // Dependency set used in execution is union set of all dependencies of the follower quorum
    // defined initially by the DepPropose
    // TODO Kai: do we have to include depPropose dependencies here?
    var depVerifies = this.slot.getDepVerifies().values().stream().toList();
    var unionDepsFollowerQuorum = DepVerifyMessage.unionOfDependencies(depVerifies, null);
    var executeMsg =
        new ExecuteMessage(this.seqNum, this.slot.getRequest(), unionDepsFollowerQuorum);
    this.requestExecutor.forwardRequestToExecution(executeMsg);
  }

  // endregion

  // region Reconciliation Path
  /** Pseudocode line 72-75 */
  private void enterReconciliationPath(String depVerifiesFollowerQuorumHash) {
    this.slot.setStep(AgreementSlotPhase.RP_VERIFIED);
    var prepareMsg =
        new PrepareMessage(
            this.seqNum,
            this.slot.getViewNumber(),
            this.ownReplicaId,
            depVerifiesFollowerQuorumHash);
    var wrapper = new ISOSMessageWrapper(prepareMsg, this.ownReplicaId);
    // We have to process our own prepare message as well, so includeSelf is true
    this.msgSender.broadcastToReplicas(true, wrapper);
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
  private void handleReceivedPrepareMessage(PrepareMessage prepare) {
    // a correct replica that has reached fp-verified does not contribute to the reconciliation
    // path.

    // We have to check our DepVerify hash as well
    // Set of previously received DepVerifies
    String depVerifyHash = this.slot.getDepVerifyHashCached();

    if (!depVerifyHash.equals(prepare.depVerifiesHash())) {
      logger.warn("Hash mismatch with received prepare message, throwing message away");
      return;
    }

    // Save to prepare quorum
    this.bufferedMessages.storePrepare(prepare);

    // Before we can continue processing, we need to fulfill the preconditions
    if (this.slot.getStep() != AgreementSlotPhase.RP_VERIFIED) {
      logger.info("Step mismatch");
      return;
    }

    if (!this.slot.getViewNumber().equals(prepare.viewNumber())) {
      logger.info(
          "View number mismatch in prepare, own view number: {}, received: {}",
          this.slot.getViewNumber(),
          prepare.viewNumber());
      return;
    }

    // If we have a 2f+1 quorum, we can continue
    // TODO Kai: do we need to check for other conditions of the quorum? Same hash?
    if (!this.bufferedMessages.prepareQuorumReached(
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
  }

  /**
   * Preconditions are similar to {@link #handleReceivedPrepareMessage(PrepareMessage)}.
   *
   * @param commit
   */
  private void handleReceivedCommitMessage(CommitMessage commit) {
    var depVerifies = this.slot.getDepVerifies().values().stream().toList();
    String depVerifyHash = this.slot.getDepVerifyHashCached();

    if (!depVerifyHash.equals(commit.depVerifiesHash())) {
      logger.warn("Hash mismatch with received commit message, throwing message away");
      return;
    }

    // save to quorum
    this.bufferedMessages.storeCommit(commit);

    // Before we can continue processing, we need to fulfill the preconditions
    if (this.slot.getStep() != AgreementSlotPhase.RP_PREPARED) {
      logger.info(
          "Step mismatch while handling commit message. Current step is {}", this.slot.getStep());
      return;
    }

    if (!this.slot.getViewNumber().equals(commit.viewNumber())) {
      logger.info(
          "View number mismatch in commit, own view number: {}, received: {}",
          this.slot.getViewNumber(),
          commit.viewNumber());
      return;
    }

    if (!this.bufferedMessages.commitQuorumReached(
        this.slot.getViewNumber(), (2 * this.maxFaults) + 1)) {
      logger.info(
          "Received commit message for view {}, but quorum not reached yet.",
          this.slot.getViewNumber());
      return;
    }

    logger.info("We have reached 2f+1 RpCommit messages for view {}!", this.slot.getViewNumber());

    this.slot.setStep(AgreementSlotPhase.RP_COMMITTED);
    this.cancelTimeout(ISOSTimeoutType.COMMIT);

    // ISOS Paper: ...together with the union of the dependency sets of all DepVerifys and the
    // associated DepPropose.
    var unionDepsFollowerQuorum =
        DepVerifyMessage.unionOfDependencies(depVerifies, this.slot.getDepPropose());
    var executeMsg =
        new ExecuteMessage(this.seqNum, this.slot.getRequest(), unionDepsFollowerQuorum);
    this.requestExecutor.forwardRequestToExecution(executeMsg);
  }

  // endregion

  // region View Change

  /**
   * Upon move to new view for slot. This function is called when a timeout expires.
   *
   * <p>Pseudocode line 86-100
   */
  private void moveToNewView() {
    // Paper: Once a replica decides to abort a view, the replica stops to process requests for the
    // old view and broadcasts a ViewChange message for the new view.

    // Move to new view v_s_j+1
    var previousViewNum = this.slot.getViewNumber();
    var newViewNum = ViewNumber.increaseViewNumber(previousViewNum);
    logger.info("Move from view {} to new view {}", previousViewNum, newViewNum);

    // If propose timeout is active, trigger its expiry (i.e., timeout logic should be executed now)
    this.triggerTimeoutExpiry(ISOSTimeoutType.PROPOSE);

    this.cancelTimeout(ISOSTimeoutType.COMMIT);
    this.cancelTimeout(ISOSTimeoutType.VIEWCHANGE);

    DepProposeMessage dp = this.slot.getDepPropose();
    List<DepVerifyMessage> dv = this.slot.getDepVerifies().values().stream().toList();

    // has to be 2f matching DepVerifies in both cases
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
  }

  /**
   * This function determines the coordinator for a given view and sequence number.
   *
   * @param originalCoordinatorId $s_j.co$: (original) Coordinator Id of the agreement slot
   * @param currentViewNumber: $v_{s_j}$: View number of the new view
   * @param replicaCount: $N$: current count of replicas, used for m
   */
  private static int getNextViewCoordinator(
      int originalCoordinatorId, int currentViewNumber, int replicaCount) {
    return (originalCoordinatorId + Math.max(0, currentViewNumber)) % replicaCount;
  }

  /**
   * Pseudocode line 101-107, 109-116
   *
   * @param viewChange
   */
  private void handleReceivedViewChangeMessage(ViewChangeMessage viewChange) {
    // TODO Kai: Here, we have to differentiate whether we are the coordinator or not


  }

  /**
   * Broadcasted by the View-change coordinator.
   *
   * @param newView
   */
  private void handleReceivedNewViewMessage(NewViewMessage newView) {}

  // endregion

  /** Processes incoming messages from the queue in a loop. */
  @Override
  public void run() {
    // We have to differentiate whether the Queue Processor was created due to a received
    // ClientRequest, or just a Replica Message

    // When we received a ClientRequest, the AgreementSlot request is already populated
    if (this.slot.getRequest() != null) {
      handleReceivedClientRequest();
    }

    while (!Thread.currentThread().isInterrupted()) {
      try {
        ISOSMessage msg = this.incomingQueue.take();
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
}
