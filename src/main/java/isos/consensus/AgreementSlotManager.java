package isos.consensus;

import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.RequestReceiver;
import isos.communication.ClientMessageWrapper;
import isos.communication.MessageSender;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.model.AgreementSlot;
import isos.consensus.model.AgreementSlotSequence;
import isos.consensus.model.SequenceNumber;
import isos.consensus.model.TimeoutConfiguration;
import isos.execution.ExecutableRequestReceiver;
import isos.execution.graph.ClientPayloadDeserializer;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class maintains an AgreementSlotSequence for each replica.
 *
 * <p>Additionally, it pre-sorts incoming messages according to their sequence number so that
 * agreement slot-specific worker threads
 */
public class AgreementSlotManager implements RequestReceiver {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int SEQUENCE_LOG_INTERVAL = 100;

  /** State Storage */
  private final ReplicaId ownReplicaId;

  private final int agreementSlotSequenceLength;
  private final ConcurrentMap<ReplicaId, AgreementSlotSequence> replicaAgreementSlots;

  /** Message handling threads / supporting data structures */
  private final Map<SequenceNumber, BlockingQueue<ISOSMessage>> queueProcessorInputQueue;

  private final Map<SequenceNumber, Thread> queueProcessorThreads;
  private final Map<SequenceNumber, AgmtSlotQueueProcessor> queueProcessors;
  // Starting and reacting to timeouts happens inside the QueueProcessor
  private final ScheduledExecutorService timeoutExecutor;
  private final TimeoutConfiguration timeoutConfig;

  /** Callbacks for threads to communicate with services */
  private MessageSender msgSender; // Handler for outgoing messages from the QueueProcessors

  /** Callback when a request was committed and can be executed (with the dependency graph). */
  private final ExecutableRequestReceiver executableRequestReceiver;

  /** Callback to deserialize the client payload when a new client request is received. */
  private final ClientPayloadDeserializer clientPayloadDeserializer;

  /**
   * Callback to get the conflicts of a given request. Has to be definable by the application
   * developer, so we use a callback function
   */
  private final ConflictChecker conflictChecker;

  private final int maxFaults;
  private final int replicaCount;

  private final Thread proposer;

  // Message batching
  private final int batchTimeout;
  private final int maxBatchCount;
  private final int maxBatchBytes;
  private final PendingRequestBuffer pendingRequests;

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
      int agreementSlotSequenceLength,
      ConflictChecker conflictChecker,
      ExecutableRequestReceiver executableRequestReceiver,
      ClientPayloadDeserializer clientPayloadDeserializer,
      MessageSender msgSender,
      int maxFaults,
      int replicaCount,
      int maxBatchCount,
      int maxBatchBytes,
      int batchTimeout) {
    this.ownReplicaId = ownReplicaId;
    this.timeoutConfig = timeoutConfig;
    this.timeoutExecutor = new ScheduledThreadPoolExecutor(4);
    this.agreementSlotSequenceLength = agreementSlotSequenceLength;
    this.replicaAgreementSlots = new ConcurrentHashMap<>();
    this.queueProcessorInputQueue = new HashMap<>();
    this.queueProcessorThreads = new HashMap<>();
    this.queueProcessors = new HashMap<>();
    this.conflictChecker = conflictChecker;
    this.executableRequestReceiver = executableRequestReceiver;
    this.clientPayloadDeserializer = clientPayloadDeserializer;
    this.msgSender = msgSender;
    this.maxFaults = maxFaults;
    this.replicaCount = replicaCount;

    // Message batching
    this.batchTimeout = batchTimeout;
    this.maxBatchCount = maxBatchCount;
    this.maxBatchBytes = maxBatchBytes;
    this.pendingRequests = new PendingRequestBuffer(batchTimeout, maxBatchCount, maxBatchBytes);

    this.proposer = Thread.ofVirtual().start(this::runProposeThread);

    // Create agreement slot sequences for own replica and other replicas
    // Start virtual threads of own AgreementSlotSequence one by one, but start threads of other
    // sequences at once
    this.initializeReplicaId(ownReplicaId, false); // Do not start
    for (var rId : replicaIds) {
      if (ownReplicaId.equals(rId)) {
        continue;
      }
      this.initializeReplicaId(rId, true);
    }
  }

  /**
   * Creates the sequence, threads, and inputQueue for all elements of the sequence.
   *
   * @param replicaId
   */
  private void initializeReplicaId(ReplicaId replicaId, boolean startNewThread) {
    var sequence = new AgreementSlotSequence(replicaId, agreementSlotSequenceLength);
    this.replicaAgreementSlots.put(replicaId, sequence);

    for (int i = 0; i < agreementSlotSequenceLength; i++) {
      var seqNum = SequenceNumber.of(replicaId, i);
      this.initializeEmptyAgreementSlot(seqNum, startNewThread);
    }
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
  private void initializeEmptyAgreementSlot(SequenceNumber newSlot, boolean startNewThread)
      throws IllegalArgumentException, IndexOutOfBoundsException {
    // check whether there is already a queue processor or not
    if (this.queueProcessorThreads.containsKey(newSlot)) {
      logger.info("{}", queueProcessorThreads.keySet());
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
            this.ownReplicaId,
            newSlot,
            inputQueue,
            this.timeoutConfig,
            this.timeoutExecutor,
            this.msgSender,
            slot,
            this.conflictChecker,
            this::waitForDeps,
            this.executableRequestReceiver,
            this.maxFaults,
            this.replicaCount);
    Thread newQueueProcessorThread =
        Thread.ofVirtual().name("AgmtSlot" + newSlot).unstarted(queueProcessor);
    this.queueProcessorThreads.put(newSlot, newQueueProcessorThread);
    this.queueProcessors.put(newSlot, queueProcessor);
    if (startNewThread) {
      newQueueProcessorThread.start();
      this.replicaAgreementSlots
          .get(replicaId)
          .updateLowestUninitialized(newSlot.sequenceCounter() + 1);
    }
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
                processorSlot.awaitConditionCompleted(this.maxFaults);
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
  public void handleReplicaMessage(SystemMessage sm) {
    if (sm instanceof ISOSMessageWrapper isosMsg) {
      ISOSMessage payload = isosMsg.getPayload();
      SequenceNumber seqNum = payload.seqNum();

      var thread = this.queueProcessorThreads.get(seqNum);
      if (thread == null) {
        logger.error("Invalid Sequence Number {}. Throwing away message", seqNum);
        return;
      }
      try {
        thread.start();
      } catch (IllegalThreadStateException ignored) {
      }

      logger.debug("Received {} message from replica {}", payload.msgType(), sm.getSender());

      // If the received request from a replica is a depPropose with a request, deserialize the
      // payload / update the cache before
      if (payload instanceof DepProposeWithRequest depPropose) {
        try {
          if (depPropose.requests() != null) {
            for (var r : depPropose.requests().getRequests()) {
              r.updateDeserializedCommandCache(clientPayloadDeserializer);
            }
          }

          if (sm.getSender() != depPropose.logicalSender().value()) {
            // Request of depPropose can be null if it was broadcasted when the propose timeout
            // expired
            logger.warn(
                "Received DepPropose with mismatching physical ({}) and logical ({}) sender. DepPropose timeout expired?",
                sm.getSender(),
                depPropose.logicalSender().value());
          }

        } catch (IOException | ClassNotFoundException e) {
          logger.error(
              "Error while decoding client request: {}. Throwing received DepProposeWithRequest away.",
              e.getMessage());
          return;
        }

        if (seqNum.sequenceCounter() % SEQUENCE_LOG_INTERVAL == 0) {
          logger.info(
              "---------- Reached DepPropose sequence number {} for replica {}",
              seqNum.sequenceCounter(),
              seqNum.replicaId());
        }
      }

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

  /**
   * Request received from Client
   *
   * <p>Requirement: To start the fast path, the coordinator selects its agreement slot
   *
   * <p>Pseudocode line 10-19
   *
   * @param msg The request delivered by the TOM layer
   * @param fromClient If the request was received from a client
   */
  @Override
  public void requestReceived(ClientMessageWrapper msg, boolean fromClient) {
    // Assert r correctly signed -> should done in networking layer

    try (ByteArrayInputStream bis = new ByteArrayInputStream(msg.getPayload());
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      OrderedClientRequest r = (OrderedClientRequest) ois.readObject();

      if (r == null) {
        logger.warn("Received command from client without ClientRequest. Throwing message away");
        return;
      }

      // Deserialize the payload into an object and cache it, so that it does not have to be
      // deserialized multiple times (e.g., while determining conflicts between client requests, or
      // for the command execution)
      // We are passing in a function that deserializes the command instead of reading the payload
      // and deserializing it here, so that
      r.updateDeserializedCommandCache(clientPayloadDeserializer);


      // when we receive a new request, we first add it to the pending requests.
      // set the request for the latest entry in th
      // AgreementSlots
      this.pendingRequests.addPendingRequest(r);
    } catch (IOException | ClassNotFoundException e) {
      logger.error(
          "Error while decoding client request: {}. Throwing client request away", e.getMessage());
    }
  }

  private void runProposeThread() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        var requests = this.pendingRequests.awaitPendingRequests();

        if (requests.getRequests().length == 0) {
            logger.warn("Proposed batch is empty!");
            continue;
        }

        SequenceNumber newSlot =
            this.replicaAgreementSlots.get(ownReplicaId).createLowestSeqNumEntry(requests);

        if (newSlot.sequenceCounter() % SEQUENCE_LOG_INTERVAL == 0) {
          logger.info(
              "---------- Reached {} client requests for replica {}",
              newSlot.sequenceCounter() + 1,
              this.ownReplicaId);
        }

        // Start the thread
        this.queueProcessorThreads.get(newSlot).start();
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for next batch, exiting propose Thread");
    }
  }
}
