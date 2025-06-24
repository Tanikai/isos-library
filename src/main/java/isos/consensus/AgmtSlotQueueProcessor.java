package isos.consensus;

import isos.communication.MessageSender;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.ISOSMessageWrapper;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import java.util.Map;
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

  // Sequence number that this queue processor is responsible for
  private final ReplicaId ownReplicaId;
  private final SequenceNumber seqNum;
  private boolean running;
  // Queue of incoming messages
  private BlockingQueue<ISOSMessage> incomingQueue;
  //
  private TimeoutConfiguration timeoutConfig;

  // store messages until a Quorum size is reached
  private Map<ISOSMessageType, Map<ReplicaId, ISOSMessage>> waitingForQuorum;

  private MessageSender msgSender;

  public AgmtSlotQueueProcessor(
      ReplicaId ownReplicaId,
      SequenceNumber seqNum,
      BlockingQueue<ISOSMessage> incomingQueue,
      TimeoutConfiguration timeoutConfig,
      MessageSender msgSender) {
    this.ownReplicaId = ownReplicaId;
    this.seqNum = seqNum;
    this.incomingQueue = incomingQueue;
    this.timeoutConfig = timeoutConfig;
    this.msgSender = msgSender;

    this.logger = LoggerFactory.getLogger(String.format("QueueProcessor %s", seqNum.toString()));
  }

  /** Processes incoming messages from the queue in a loop. */
  @Override
  public void run() {
    this.running = true;

    while (running) {
      try {
        ISOSMessage msg = this.incomingQueue.take();
        logger.info(
            "Queue received message of type {} from sender {}", msg.msgType(), msg.logicalSender());

        if (msg instanceof DepProposeMessage depPropose) {
          // Line 22: assert F is valid fast-path quorum

          // Line 23: First propose from coordinator
          // wait
          var responsePayload =
              new DepVerifyMessage(depPropose.seqNum(), ownReplicaId, "asdb", new DependencySet());
          this.msgSender.broadcastToReplicas(
              true, new ISOSMessageWrapper(responsePayload, ownReplicaId.value()));

        } else if (msg instanceof DepVerifyMessage depVerify) {

        }
      } catch (InterruptedException e) {
        // interrupted while waiting to take new message from incomingQueue

      }
    }
  }
}
