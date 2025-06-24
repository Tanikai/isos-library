package isos.consensus;

import bftsmart.communication.MessageHandler;
import bftsmart.communication.SystemMessage;
import isos.communication.MessageSender;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageWrapper;
import isos.utils.ReplicaId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class maintains an AgreementSlotSequence for each replica. Additionally,
 *
 * <p>it pre-sorts incoming messages according to their sequence number so that agreement
 * slot-specific worker threads
 */
public class AgreementSlotManager implements MessageHandler {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** State Storage */
  private final ReplicaId ownReplicaId;

  private final ConcurrentMap<ReplicaId, AgreementSlotSequence> replicaAgreementSlots;

  /** Message handling threads / supporting data structures */
  private Map<SequenceNumber, BlockingQueue<ISOSMessage>> queueProcessorInputQueue;

  private Map<SequenceNumber, Thread> queueProcessorThreads;
  // Starting and reacting to timeouts happens inside the QueueProcessor
  private TimeoutConfiguration timeoutConfig;

  /** Callbacks for threads to communicate with services */
  private MessageSender msgSender; // Handler for outgoing messages from the QueueProcessors

  /**
   * @param timeoutConfig
   */
  public AgreementSlotManager(
      ReplicaId ownReplicaId, TimeoutConfiguration timeoutConfig, ReplicaId[] replicaIds) {
    this.ownReplicaId = ownReplicaId;
    this.timeoutConfig = timeoutConfig;
    this.replicaAgreementSlots = new ConcurrentHashMap<>();
    this.queueProcessorInputQueue = new HashMap<>();
    this.queueProcessorThreads = new HashMap<>();
    for (var rId : replicaIds) {
      this.replicaAgreementSlots.put(rId, new AgreementSlotSequence(rId));
    }
  }

  public void initialize(MessageSender msgSender) {
    this.msgSender = msgSender;
  }

  /**
   * Initializes the queueProcessor thread and inputQueue for the given sequence number. To
   * initialize the agreementSlot in the sequence, call the AgreementSlotSequence object.
   *
   * @param newSlot
   */
  private void initializeEmptyAgreementSlot(SequenceNumber newSlot)
      throws IllegalArgumentException {
    // check whether there is already a queue processor or not
    if (this.queueProcessorThreads.containsKey(newSlot)) {
      throw new IllegalArgumentException(
          String.format("Slot %s is already initialized", newSlot.toString()));
    }

    // Create queue to communicate
    BlockingQueue<ISOSMessage> inputQueue = new LinkedBlockingQueue<>();
    this.queueProcessorInputQueue.put(newSlot, inputQueue);

    // Create processor for consensus algorithm
    Thread newQueueProcessor =
        Thread.ofVirtual()
            .name("AgmtSlot" + newSlot.toString())
            .unstarted(
                new AgmtSlotQueueProcessor(
                    ownReplicaId, newSlot, inputQueue, timeoutConfig, msgSender));
    this.queueProcessorThreads.put(newSlot, newQueueProcessor);
    newQueueProcessor.start();
  }

  /**
   * Used when this replica receives a message. Guarantees that all agreementSlots from 0 up to
   * newSeqNum have an inputQueue and agreementSlotProcessor.
   */
  public void createSequenceNumberEntry(SequenceNumber newSeqNum) {
    // Fast check: if newSeqNum is already initialized, return early
    if (this.replicaAgreementSlots.containsKey(newSeqNum)) {
      return;
    }

    // Else: We have to initialize the sequence numbers from the lowest uninitialized agreementSlot
    ReplicaId replicaId = newSeqNum.replicaIdRec();
    AgreementSlotSequence sequence = this.replicaAgreementSlots.get(replicaId);
    var createdNums =
        sequence.batchCreateSequenceNumberUntil(
            newSeqNum); // batch operation is more efficient due to locking

    // For all created agreementSlots, create a worker thread
    for (var seq : createdNums) {
      initializeEmptyAgreementSlot(seq);
    }
  }

  /**
   * Used when this replica receives a
   *
   * @param replicaId
   * @return
   */
  public SequenceNumber createLowestUnusedSequenceNumberEntry(ReplicaId replicaId) {
    SequenceNumber newSlot =
        this.replicaAgreementSlots.get(replicaId).createLowestUnusedSequenceNumberEntry();
    this.initializeEmptyAgreementSlot(newSlot);

    return newSlot;
  }

  public Map<ReplicaId, List<AgreementSlot>> getUsedAgreementSlots() {
    return replicaAgreementSlots.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAgreementSlotsReadOnly()));
  }

  /**
   * New message received from other replica.
   *
   * @param sm
   */
  @Override
  public void processData(SystemMessage sm) {
    if (sm instanceof ISOSMessageWrapper isosMsg) {
      ISOSMessage payload = isosMsg.getPayload();
      SequenceNumber seqNum = payload.seqNum();

      // TODO Kai: There should be check whether the newSeqNum is too large. For example, when it
      // skips 10 slots

      // Guarantee that the respective AgreementSlot is initialized (Processor thread, input queue,
      // agreementSlot data structure)
      createSequenceNumberEntry(seqNum);

      var queue = this.queueProcessorInputQueue.get(seqNum);
      queue.offer(payload);
    } else {
      logger.error("invalid message passed to processData (not a ISOSWrapperMessage)");
    }
  }

  @Override
  public void verifyPending() {}
}
