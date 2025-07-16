package isos.consensus;

import isos.communication.MessageSender;
import isos.graph.DependencyWaitFunction;
import isos.graph.RequestConflictChecker;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.ISOSMessageWrapper;
import isos.message.fast.DepCommitMessage;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepProposeWithRequest;
import isos.message.fast.DepVerifyMessage;
import isos.message.reconciliation.CommitMessage;
import isos.message.reconciliation.PrepareMessage;
import isos.message.viewchange.NewViewMessage;
import isos.message.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles incoming messages and delegates them to subtasks, depending on the current
 * state. In other words, it contains and executes the (core) business logic of ISOS. Additionally,
 * it handles timeouts and their logic if they expire.
 */
public class AgmtSlotQueueProcessor implements Runnable {
  private final Logger logger;

  private final ReplicaId ownReplicaId; // Id of the *running* replica, not of the agreement slot
  private final SequenceNumber
      seqNum; // Sequence number that this queue processor is responsible for

  // Messaging

  // Queue of incoming messages
  private final BlockingQueue<ISOSMessage> incomingQueue;

  // Messages that have been deferred because we were not in the correct step yet. After the
  // preconditions are met, they can be processed.
  private Queue<ISOSMessage> deferredQueue;

  //
  private final TimeoutConfiguration timeoutConfig;
  private final ScheduledExecutorService timeoutExecutor;
  private final Map<ISOSTimeoutType, ScheduledFuture> currentTimeouts;

  // Store processed messages until a Quorum size is reached.
  private Map<ISOSMessageType, Map<ReplicaId, ISOSMessage>> waitingForQuorum;

  private final MessageSender msgSender;

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

  /**
   * The conflictChecker returns the conflicts of a ClientRequest to all other ClientRequests that
   * we have already received.
   *
   * <p>Passed from the outside due to access to other agreement slots.
   */
  private final RequestConflictChecker conflictChecker;

  /** Passed from the outside due to access to other agreement slots. */
  private final DependencyWaitFunction dependencyWait;

  private final int quorumF;

  public AgmtSlotQueueProcessor(
      ReplicaId ownReplicaId,
      SequenceNumber seqNum,
      BlockingQueue<ISOSMessage> incomingQueue,
      TimeoutConfiguration timeoutConfig,
      MessageSender msgSender,
      AgreementSlot slot,
      RequestConflictChecker conflictChecker,
      DependencyWaitFunction dependencyWait,
      int quorumF) {
    this.ownReplicaId = ownReplicaId;
    this.seqNum = seqNum;
    this.incomingQueue = incomingQueue;
    this.timeoutConfig = timeoutConfig;
    this.timeoutExecutor =
        new ScheduledThreadPoolExecutor(2); // TODO Kai: how to determine the corePoolSize?
    this.currentTimeouts = new HashMap<>();
    this.msgSender = msgSender;
    this.slot = slot;
    this.slotLock = new ReentrantLock();
    this.messageCountCondition = this.slotLock.newCondition();
    this.conflictChecker = conflictChecker;
    this.dependencyWait = dependencyWait;

    this.quorumF = quorumF;

    this.logger = LoggerFactory.getLogger(String.format("QueueProcessor %s", seqNum.toString()));
  }

  /**
   * Pseudocode line 60
   *
   * @param quorum
   * @throws InterruptedException
   */
  public void awaitWaitConditionCompleted(int quorum) throws InterruptedException {
    if (quorum < 2) {
      logger.warn("Quorum is smaller than 2 (f+1 -> f is 0), is it correct?");
    }

    this.slotLock.lock();
    try {
      while (slot.getDepPropose() == null // received valid DepPropose
          && slot.getDepVerifies().size() < quorum // received f+1 correctly signed DepVerifys
          && slot.getViewChanges().size() < quorum // received f+1 correctly signed ViewChanges
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

    // Fast path
    if (msg instanceof DepProposeWithRequest depPropose) {
      // This case only happens if we receive a depPropose from another replica. For requests where
      // the current replica acts as the coordinator, see handleReceivedClientRequest().
      this.handleReceivedDepProposeWithRequest(depPropose);
    } else if (msg instanceof DepProposeMessage depPropose) {
      logger.error("Received DepPropose message without request, throwing away");
    } else if (msg instanceof DepVerifyMessage depVerify) {
      this.handleReceivedDepVerify(depVerify);
    } else if (msg instanceof DepCommitMessage depCommit) {
      this.handleReceivedDepCommit(depCommit);
    }
    // Reconciliation path
    else if (msg instanceof PrepareMessage prepare) {
      this.handleReceivedPrepareMessage(prepare);
    } else if (msg instanceof CommitMessage commit) {
      this.handleReceivedCommitMessage(commit);
    }
    // View change
    else if (msg instanceof NewViewMessage newView) {
      this.handleReceivedNewViewMessage(newView);
    } else if (msg instanceof ViewChangeMessage viewChange) {
      this.handleReceivedViewChangeMessage(viewChange);
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
    // TODO: Get Quroum of 2f followers with lowest latency
    // For now, get random two followers
    Set<ReplicaId> followerSet = null;
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

    this.startCommitTimeout();
  }

  /** Pseudocode Line 70, 71 */
  private void startCommitTimeout() {
    if (this.currentTimeouts.containsKey(ISOSTimeoutType.COMMIT)) {
      logger.warn("A commit timeout already exists!");
      return;
    }

    var commitTimeout =
        this.timeoutExecutor.schedule(
            () -> {
              // TODO Kai: Timeout handling should be protected by a lock -> we do not want timeout
              // handler and "normal" message handler running simultaneously

              // Move to new view v_s_j+1
              var currentViewNum = this.slot.getViewNumber();
              var newViewNum = ViewNumber.increaseViewNumber(currentViewNum);
              logger.info(
                  "Timeout expired, move from view {} to new view {}", currentViewNum, newViewNum);
              this.slot.setViewNumber(newViewNum);

              // TODO Kai: we need to notify here somehow (pseudocode line 86 "upon move to new view
              // do...")
              this.currentTimeouts.remove(ISOSTimeoutType.COMMIT); // remove self from timeouts
            },
            timeoutConfig.commitTimeout,
            TimeUnit.MILLISECONDS);
    this.currentTimeouts.put(ISOSTimeoutType.COMMIT, commitTimeout);
  }

  /** Pseudocode Line 68, 69 */
  private void startProposeTimeout() {
    if (this.currentTimeouts.containsKey(ISOSTimeoutType.PROPOSE)) {
      logger.warn("A propose timeout already exists!");
      return;
    }

    var proposeTimeout =
        this.timeoutExecutor.schedule(
            () -> {
              var msg =
                  new ISOSMessageWrapper(this.slot.getDepPropose(), this.ownReplicaId.value());
              // TODO Kai: but send without request or what?

              this.msgSender.broadcastToReplicas(false, msg);
            },
            timeoutConfig.proposeTimeout,
            TimeUnit.MILLISECONDS);
    this.currentTimeouts.put(ISOSTimeoutType.PROPOSE, proposeTimeout);
  }

  /**
   * Pseudocode Line 20-35
   *
   * @param depProposeWithR
   */
  private void handleReceivedDepProposeWithRequest(DepProposeWithRequest depProposeWithR) {
    // Line 21: pre: step == init
    if (slot.getStep() != AgreementSlotPhase.INIT) {
      logger.info("Step mismatch, maybe duplicated DepPropose?");
      //      this.deferredQueue.add(depPropose);
      return;
    }

    var depPropose = depProposeWithR.depPropose();
    var request = depProposeWithR.request();

    // Line 22: assert F is valid fast-path quorum
    // TODO Kai: what is a valid fast-path quorum?

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

    // TODO: we have to wait in a loop with condition check
    try {
      this.dependencyWait.waitUntilConsensusStarted(waitDeps);
    } catch (InterruptedException e) {
      logger.error(
          "Interrupted while waiting for dependencies {}. Stop processing depPropose.", waitDeps);
      Thread.currentThread().interrupt();
      return;
    }

    // Line 25
    if (this.slot.getDepPropose() == null) {
      this.startCommitTimeout();
      this.startProposeTimeout();
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
        this.msgSender.broadcastToReplicas(false, wrapper);
      }
    }
  }

  private void handleReceivedDepVerify(DepVerifyMessage depVerify) {
    if (slot.getStep() != AgreementSlotPhase.PROPOSED) {
      logger.warn("Step mismatch");
      try {
        this.deferredQueue.add(depVerify);
      } catch (IllegalStateException e) {
        logger.error(
            "DeferredQueue for Sequence Number {} is full, throwing message away", this.seqNum);
      }
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
          "Already received DepVerify from follower {}. Throwing message away",
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
      // TODO Kai: While we are waiting, we cannot process any other messages. Is this fine?
      this.dependencyWait.waitUntilConsensusStarted(depVerify.depSet().dependencies());
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for dependencies of received DepVerify message.");
      return;
    }

    // add to the map
    var depVerifyMap = this.slot.getDepVerifies();
    depVerifyMap.put(depVerify.followerId(), depVerify);

    // Get all DepVerify messages that are in the follower quorum of the depPropose and filter the
    // map entries
    var followerQuorum = this.slot.getDepPropose().followerQuorum();

    var depVerifiesFromFollowerQuorum =
        depVerifyMap.values().stream()
            .filter(m -> followerQuorum.contains(m.followerId()))
            .toList();

    if (depVerifiesFromFollowerQuorum.size() < (this.quorumF * 2)) {
      return;
    }

    // TODO Kai: we might need a lock / atomic variable for canceling the timeout
    var proposeTimeout = this.currentTimeouts.get(ISOSTimeoutType.PROPOSE);
    if (proposeTimeout != null) {
      proposeTimeout.cancel(false);
    }

    // Add all dependencies to a single dependency set
    var allDeps =
        depVerifiesFromFollowerQuorum.stream()
            .flatMap(m -> m.depSet().dependencies().stream())
            .collect(Collectors.toSet());

    // Line 46: Every dependency is reported by at least f+1 followers
    var depsOk =
        allDeps.parallelStream()
            .allMatch(
                (seqNum) -> {
                  var depCount =
                      depVerifyMap.values().stream()
                          .filter(msg -> msg.depSet().dependencies().contains(seqNum))
                          .count();
                  return depCount > (this.quorumF + 1);
                });

    if (!depsOk) {
      // At least 1 dependency is not reported by at least f+1 followers

      // TODO Kai: How to enter reconciliation path, stop participating in fast path?
      return;
    }

    this.slot.setStep(AgreementSlotPhase.FP_VERIFIED);

    var depCommitMsg =
        new DepCommitMessage(
            this.seqNum,
            this.ownReplicaId,
            ConsensusUtils.calculateDepVerifyHash(depVerifiesFromFollowerQuorum));

    var wrapper = new ISOSMessageWrapper(depCommitMsg, this.ownReplicaId.value());
    this.msgSender.broadcastToReplicas(false, wrapper);
  }

  private void handleReceivedDepCommit(DepCommitMessage depCommit) {}

  private void handleReceivedPrepareMessage(PrepareMessage prepare) {}

  private void handleReceivedCommitMessage(CommitMessage commit) {}

  private void handleReceivedNewViewMessage(NewViewMessage newView) {}

  private void handleReceivedViewChangeMessage(ViewChangeMessage viewChange) {}

  /** Processes incoming messages from the queue in a loop. */
  @Override
  public void run() {
    // We have to differentiate whether the Queue Processor was created due to a received
    // ClientRequest, or just a Replica Message

    // When we received a ClientRequest, the AgreementSlot request is already populated
    if (this.slot.getRequest() != null) {
      handleReceivedClientRequest();
      // we can then set the agreement slot to step
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
