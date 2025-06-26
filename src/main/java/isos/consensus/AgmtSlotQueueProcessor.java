package isos.consensus;

import isos.communication.MessageSender;
import isos.graph.DependencyWaitFunction;
import isos.graph.RequestConflictChecker;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.ISOSMessageWrapper;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
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
  private boolean running;

  // Messaging

  // Queue of incoming messages
  private BlockingQueue<ISOSMessage> incomingQueue;

  //
  private TimeoutConfiguration timeoutConfig;

  // store messages until a Quorum size is reached
  private Map<ISOSMessageType, Map<ReplicaId, ISOSMessage>> waitingForQuorum;

  private MessageSender msgSender;

  /**
   * AgreementSlot passed from the AgreementSlotManager. If the request != null upon thread start,
   * we assume that we are the coordinator.
   */
  private AgreementSlot slot; // AgreementSlot passed from the AgreementSlotManager.

  private RequestConflictChecker conflictChecker;
  private DependencyWaitFunction dependencyWait;

  public AgmtSlotQueueProcessor(
      ReplicaId ownReplicaId,
      SequenceNumber seqNum,
      BlockingQueue<ISOSMessage> incomingQueue,
      TimeoutConfiguration timeoutConfig,
      MessageSender msgSender,
      AgreementSlot slot,
      RequestConflictChecker conflictChecker,
      DependencyWaitFunction dependencyWait
      ) {
    this.ownReplicaId = ownReplicaId;
    this.seqNum = seqNum;
    this.incomingQueue = incomingQueue;
    this.timeoutConfig = timeoutConfig;
    this.msgSender = msgSender;
    this.slot = slot;
    this.conflictChecker = conflictChecker;
    this.dependencyWait = dependencyWait;

    this.logger = LoggerFactory.getLogger(String.format("QueueProcessor %s", seqNum.toString()));
  }

  private void handleMessage(ISOSMessage msg) {
    logger.info(
        "Queue received message of type {} from sender {}", msg.msgType(), msg.logicalSender());

    if (msg instanceof DepProposeMessage depPropose) {
      this.handleReceivedDepPropose(depPropose);

    } else if (msg instanceof DepVerifyMessage depVerify) {

    }
  }

  /**
   * Called when our replica receives a request from a client and has to act as the coordinator for
   * that request.
   *
   * Pseudocode Line 10-19
   */
  private void handleReceivedClientRequest() {
    // We can only be coordinator of a client request if the generated SequenceNumber is our own
    assert Objects.equals(this.ownReplicaId, this.seqNum.replicaIdRec());
    var r = slot.getRequest();

    DependencySet depSet = this.conflictChecker.conflicts(r);
    // TODO: Get Quroum of 2f followers with lowest latency
    // For now, get random two followers
    Set<ReplicaId> followerSet = null;
    DepProposeMessage dp =
            new DepProposeMessage(
                    seqNum,
                    ownReplicaId,
                    r.calculateHash(),
                    depSet,
                    followerSet); // TODO: create constructor / factory method to create message

    throw new NotImplementedException();
    // TODO Line 17: set step of this agreement slot to proposed
    // TODO Line 18: Broadcast message to all replicas

    // TODO: Notify Listeners that a new Request was handled
    // Listeners include: Other requests that are waiting until a certain condition is fulfilled
    // (e.g., 2f messages received)
  }

  /**
   * Pseudocode Line 20-35
   *
   * @param depPropose
   */
  private void handleReceivedDepPropose(DepProposeMessage depPropose) {
    // Line 21: pre: step == init
    if (slot.getStep() != AgreementSlotPhase.INIT) {
      logger.info("Step mismatch");
      // TODO: save message for future processing
      return;
    }

    // Line 22: assert F is valid fast-path quorum

    // Line 23: First propose from coordinator

    // TODO Kai: wait for quorum
    try {
      this.dependencyWait.waitUntilConsensusStarted(new HashSet<SequenceNumber>());
    } catch (InterruptedException e) {
      // what should we do if interrupted? Just wait again? Should we check for certain conditions?
      // e.g. if we are still running or not?
    }

    var responsePayload =
        new DepVerifyMessage(depPropose.seqNum(), ownReplicaId, "asdb", new DependencySet());
    this.msgSender.broadcastToReplicas(
        true, new ISOSMessageWrapper(responsePayload, ownReplicaId.value()));
  }

  /** Processes incoming messages from the queue in a loop. */
  @Override
  public void run() {
    this.running = true;

    // We have to differentiate whether the Queue Processor was created due to a received
    // ClientRequest, or just a Replica Message

    // When we received a ClientRequest, the AgreementSlot request is already populated
    if (this.slot.getRequest() != null) {
      handleReceivedClientRequest();
    }

    while (running) {
      try {
        ISOSMessage msg = this.incomingQueue.take();
        this.handleMessage(msg);

      } catch (InterruptedException e) {
        // interrupted while waiting to take new message from incomingQueue

      }
    }
  }
}
