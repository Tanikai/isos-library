package isos.consensus;

import bftsmart.communication.MessageHandler;
import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.RequestReceiver;
import isos.communication.ClientMessageWrapper;
import isos.communication.MessageSender;
import isos.consensus.model.*;
import isos.execution.ExecutableRequestReceiver;
import isos.execution.graph.RequestConflictChecker;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageWrapper;
import isos.utils.ReplicaId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class maintains an AgreementSlotSequence for each replica.
 *
 * <p>Additionally, it pre-sorts incoming messages according to their sequence number so that
 * agreement slot-specific worker threads
 */
public class AgreementSlotManager implements MessageHandler, RequestReceiver {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** State Storage */
  private final ReplicaId ownReplicaId;

  private final ConcurrentMap<ReplicaId, AgreementSlotSequence> replicaAgreementSlots;

  /** Message handling threads / supporting data structures */
  private final Map<SequenceNumber, BlockingQueue<ISOSMessage>> queueProcessorInputQueue;

  private final Map<SequenceNumber, Thread> queueProcessorThreads;
  private final Map<SequenceNumber, AgmtSlotQueueProcessor> queueProcessors;
  // Starting and reacting to timeouts happens inside the QueueProcessor
  private final TimeoutConfiguration timeoutConfig;

  /** Callbacks for threads to communicate with services */
  private MessageSender msgSender; // Handler for outgoing messages from the QueueProcessors

  /** Callback when a request was committed and can be executed (with the dependency graph). */
  private final ExecutableRequestReceiver executableRequestReceiver;

  /**
   * Callback to get the conflicts of a given request. Has to be definable by the application
   * developer, so we use a callback function
   */
  private final RequestConflictChecker conflictChecker;

  private final int maxFaults;

  /**
   * @param ownReplicaId The id of the current replica.
   * @param timeoutConfig
   * @param replicaIds Ids of other replicas that participate in the consensus.
   * @param maxFaults
   */
  public AgreementSlotManager(
      ReplicaId ownReplicaId,
      TimeoutConfiguration timeoutConfig,
      ReplicaId[] replicaIds,
      RequestConflictChecker conflictChecker,
      ExecutableRequestReceiver executableRequestReceiver,
      int maxFaults) {
    this.ownReplicaId = ownReplicaId;
    this.timeoutConfig = timeoutConfig;
    this.replicaAgreementSlots = new ConcurrentHashMap<>();
    this.queueProcessorInputQueue = new HashMap<>();
    this.queueProcessorThreads = new HashMap<>();
    this.queueProcessors = new HashMap<>();
    // Create agreement slot sequences for own replica and other replicas
    this.replicaAgreementSlots.put(ownReplicaId, new AgreementSlotSequence(ownReplicaId));
    for (var rId : replicaIds) {
      this.replicaAgreementSlots.put(rId, new AgreementSlotSequence(rId));
    }
    this.conflictChecker = conflictChecker;
    this.executableRequestReceiver = executableRequestReceiver;
    this.maxFaults = maxFaults;
  }

  public void initialize(MessageSender msgSender) {
    this.msgSender = msgSender;
  }

  private Optional<AgreementSlot> getAgreementSlot(SequenceNumber seqNum) {
    var sequence = this.replicaAgreementSlots.get(seqNum.replicaIdRec());
    if (sequence == null) {
      return Optional.empty();
    }

    try {
      var slot = sequence.getAgreementSlotValue(seqNum);
      return Optional.ofNullable(slot);
    } catch (IndexOutOfBoundsException e) {
      return Optional.empty();
    }
  }

  /**
   * Initializes the queueProcessor, its Thread and inputQueue for the given sequence number. To
   * initialize the agreementSlot in the sequence, call the AgreementSlotSequence object.
   *
   * @param newSlot
   */
  private void initializeEmptyAgreementSlot(SequenceNumber newSlot)
      throws IllegalArgumentException, IndexOutOfBoundsException {
    // check whether there is already a queue processor or not
    if (this.queueProcessorThreads.containsKey(newSlot)) {
      throw new IllegalArgumentException(
          String.format("Slot %s is already initialized", newSlot.toString()));
    }

    // Create queue to communicate
    BlockingDeque<ISOSMessage> inputQueue = new LinkedBlockingDeque<>();
    this.queueProcessorInputQueue.put(newSlot, inputQueue);

    // Get agreement slot object for the thread processor
    var replicaId = newSlot.replicaIdRec();
    AgreementSlot slot = this.replicaAgreementSlots.get(replicaId).getAgreementSlotValue(newSlot);

    // Create processor for consensus algorithm
    // The QueueProcessor directly updates the fields in the AgreementSlot object.
    var queueProcessor =
        new AgmtSlotQueueProcessor(
            ownReplicaId,
            newSlot,
            inputQueue,
            timeoutConfig,
            msgSender,
            slot,
            this.conflictChecker,
            this::waitForDeps,
            this.executableRequestReceiver,
            this.maxFaults);
    Thread newQueueProcessorThread =
        Thread.ofVirtual().name("AgmtSlot" + newSlot).unstarted(queueProcessor);
    this.queueProcessorThreads.put(newSlot, newQueueProcessorThread);
    this.queueProcessors.put(newSlot, queueProcessor);
    newQueueProcessorThread.start();
  }

  /**
   * Used when this replica receives a message. Guarantees that all agreementSlots from 0 up to
   * newSeqNum have an inputQueue and agreementSlotProcessor.
   */
  public void createSequenceNumberEntry(SequenceNumber newSeqNum) {
    // Fast check: if newSeqNum is already initialized, return early
    //    if (this.replicaAgreementSlots.containsKey(newSeqNum)) {
    //      return;
    //    }
    // TODO Kai: this is wrong

    // Else: We have to initialize the sequence numbers from the lowest uninitialized agreementSlot
    ReplicaId replicaId = newSeqNum.replicaIdRec();
    AgreementSlotSequence sequence = this.replicaAgreementSlots.get(replicaId);

    if (sequence == null) {
      throw new RuntimeException(
          String.format("Sequence for ReplicaId %s was not yet initialized. ", replicaId));
    }

    var createdNums =
        sequence.batchCreateSequenceNumberUntil(
            newSeqNum); // batch operation is more efficient due to locking

    // For all created agreementSlots, create a worker thread
    for (var seq : createdNums) {
      initializeEmptyAgreementSlot(seq);
    }
  }

  public Map<ReplicaId, List<AgreementSlot>> getUsedAgreementSlots() {
    return replicaAgreementSlots.entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAgreementSlotsReadOnly()));
  }

  /**
   * Waits for all dependencies in the Dependency Set. Called by QueueProcessors that have to wait
   * for progress of their dependencies in order to proceed.
   *
   * <p>Requirement: "Followers strictly process the DepProposes of a coordinator in increasing
   * order of their sequence numbers, thereby ensuring that a coordinator cannot skip any sequence
   * numbers. Furthermore, they only compile and send the DepVerify for a DepPropose once they know
   * that consensus processes have been initiated for all agreement slots listed in the DepPropose's
   * dependency set. A follower has confirmation of the start of the consensus process if it fully
   * processed a DepPropose, received f+1 DepVerifys, or triggered a view change for a slot.
   *
   * <p>Pseudocode: Line 60
   *
   * @param depSet
   */
  public void waitForDeps(Set<SequenceNumber> depSet) throws InterruptedException {
    CountDownLatch allDepLatch = new CountDownLatch(depSet.size());

    // TODO Kai: maybe use structured concurrency for this use case?
    try (var taskExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var dep : depSet) {
        taskExecutor.submit(
            () -> {
              var processorSlot = this.queueProcessors.get(dep);
              try {
                processorSlot.awaitWaitConditionCompleted(this.maxFaults);
                // After this, the condition of the agreement slot is
                allDepLatch.countDown();
              } catch (InterruptedException e) {
                // what to do here? Rethrow interrupted exception?
                logger.error("Interrupted while waiting for slot {}", dep);
              } catch (Exception e) {
                logger.error("Exception in waitForDeps for slot {}: {}", dep, e.getMessage());
              }
            });
      }
    }

    // For CountDownLatch, we do not have to wait in a while loop
    allDepLatch.await();
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
      try {
        queue.add(payload);
      } catch (IllegalStateException e) {
        logger.error("Maximum capacity reached for queue {}. Throwing message away", seqNum);
      }
    } else {
      logger.error("invalid message passed to processData (not a ISOSWrapperMessage)");
    }
  }

  @Override
  public void verifyPending() {}

  /**
   * Request received from Client Requirement: To start the fast path, the coordinator selects its
   * agreement slot
   *
   * <p>Pseudocode line 10-19
   *
   * @param msg The request delivered by the TOM layer
   * @param fromClient If the request was received from a client
   */
  @Override
  public void requestReceived(ClientMessageWrapper msg, boolean fromClient) {
    // TODO Kai line 11: assert r correctly signed (-> should be done in networking layer)

    try (ByteArrayInputStream bis = new ByteArrayInputStream(msg.getPayload());
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      OrderedClientRequest r = (OrderedClientRequest) ois.readObject();
      // when we receive a new request, we create a new entry in our own sequence
      SequenceNumber newSlot =
          this.replicaAgreementSlots.get(ownReplicaId).createLowestUnusedSequenceNumberEntry(r);
      // then initialize the agreement slot with the thread
      this.initializeEmptyAgreementSlot(newSlot);

    } catch (IOException e) {
      // FIXME
      logger.warn("");
    } catch (ClassNotFoundException e) {
      // FIXME
      logger.warn("");
    }
  }
}
