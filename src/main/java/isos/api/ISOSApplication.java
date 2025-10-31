package isos.api;

import bftsmart.communication.MessageHandler;
import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.SystemMessage;
import bftsmart.configuration.ConfigurationManager;
import isos.communication.ClientMessageWrapper;
import isos.consensus.AgreementSlotManager;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.dependency.TrivialConflictChecker;
import isos.consensus.model.SequenceNumber;
import isos.consensus.model.TimeoutConfiguration;
import isos.execution.CommittedCommand;
import isos.execution.ExecuteInApplication;
import isos.execution.manager.ExecutionManager;
import isos.execution.manager.ISOSExecutionManager;
import isos.execution.graph.ClientPayloadDeserializer;
import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.builder.TrivialDependencyGraphBuilder;
import isos.execution.scc.SccFinder;
import isos.execution.scc.TarjanSCC;
import isos.message.client.OrderedClientReply;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.function.BiPredicate;

/**
 * DECIDED Kai: Maybe interface instead of class? This class is used as the central manager of the
 * replica-side state and logic. -> Use class as baseline, and let users extend certain parts of
 * logic via inheritance, or pass lambda functions to the constructor.
 */
public class ISOSApplication implements MessageHandler {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  // FIXME Kai: Timeout value should be dynamic, determined by round trip time
  private final TimeoutConfiguration timeoutConf;
  private final ReplicaId ownReplicaId;

  /**
   * DECIDED Kai: AgreementSlotSequence that contains slots of all replicas, or
   * AgreementSlotSequence per replica? -> All slots of replicas
   */
  private final AgreementSlotManager agrSlotManager;

  /**
   * Send and receive messages with scs.
   *
   * <ul>
   *   <li>MessageHandler: Callback for messages received from other replicas.
   *   <li>RequestReceiver: Callback for requests from clients.
   * </ul>
   */
  private final ServerCommunicationSystem scs;

  private final ConfigurationManager configManager;

  /**
   * The conflict checker uses a dependency graph as well to determine the direct dependencies for
   * the compact dependency set.
   */
  private final ConflictChecker conflictChecker;

  /** Used to find the strongly connected components of a dependency graph. */
  private final SccFinder sccFinder;

  /**
   * Used to generate the dependency set and determine the strongly connected components for
   * execution.
   */
  private final DependencyGraphBuilder dependencyGraphBuilder;

  private final ISOSExecutionManager executionManager;
  private final Thread executionManagerThread;

  private final ClientPayloadDeserializer deserializer;

  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public ISOSApplication(
      ConfigurationManager configManager,
      ClientPayloadDeserializer deserializer,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict,
      ExecuteInApplication executor)
      throws Exception {
    this.configManager = configManager;
    this.initializeOptimizations();
    this.timeoutConf =
        new TimeoutConfiguration(
            this.configManager.getStaticConf().getInitialIsosTimeoutDeltaMillis());
    this.deserializer = deserializer;
    this.ownReplicaId = ReplicaId.of(configManager.getStaticConf().getProcessId());

    this.sccFinder = new TarjanSCC();

    // Conflicts
    this.defaultConflict = (a, b) -> a.clientId() == b.clientId();
    this.applicationConflict = applicationConflict;
    this.conflictChecker =
        new TrivialConflictChecker(this.sccFinder, this.defaultConflict, this.applicationConflict);

    var maxFaults = configManager.getStaticConf().getF();
    var replicaCount = configManager.getStaticConf().getN();

    this.scs = new ServerCommunicationSystem(configManager, this);

    this.agrSlotManager =
        new AgreementSlotManager(
            ownReplicaId,
            timeoutConf,
            configManager.getStaticConf().getInitialViewAsReplicaId(),
            configManager.getStaticConf().getAgreementSlotSequenceLength(),
            this.conflictChecker,
            this::receiveCommittedRequest,
            deserializer,
            this.scs,
            maxFaults,
            replicaCount);

    this.scs.setRequestReceiver(this.agrSlotManager);

    // Request Execution
    this.dependencyGraphBuilder =
        new TrivialDependencyGraphBuilder(
            this.configManager.getStaticConf().getExecutionWindowSize());
    this.executionManager =
        new ExecutionManager(
            this.dependencyGraphBuilder,
            this.sccFinder,
            executor,
            this.configManager.getStaticConf().getMaxBatchSize());
    this.executionManagerThread = Thread.ofVirtual().start(this.executionManager);
  }

  private void initializeOptimizations() {
    var c = this.configManager.getStaticConf();

    if (c.isViewNumberCacheMapEnabled()) {
      logger.info("OPT: ViewNumberCacheMap is enabled");
      ViewNumber.setInstanceStrategy(new ViewNumber.ViewNumberCacheMapStrategy());
    } else {
      logger.info("OPT: ViewNumberCacheMap is disabled");
    }

    if (c.isReplicaIdCacheMapEnabled()) {
      logger.info("OPT: ReplicaIdCacheMap is enabled");
      ReplicaId.setInstanceStrategy(new ReplicaId.ReplicaIdCacheMapStrategy());
    } else {
      logger.info("OPT: ReplicaIdCacheMap is disabled");
    }

    if (c.isSequenceNumberCacheMapEnabled()) {
      logger.info("OPT: SequenceNumberCacheMap is enabled");
      SequenceNumber.setInstanceStrategy(new SequenceNumber.SequenceNumberCacheMapStrategy());
    } else {
      logger.info("OPT: SequenceNumberCacheMap is disabled");
    }
  }

  /** Starts the application by connecting to the replicas first. */
  public void start() {
    this.scs.start();
    logger.info("Wait until other replicas are connected");
    try {
      this.scs.awaitViewConnected();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while awaiting view connection.");
    }
  }

  public ServerCommunicationSystem debug_getSCS() {
    return this.scs;
  }

  /**
   * Receive a committed request from the {@link AgreementSlotManager} that can be executed by the
   * execution engine.
   *
   * @param r
   */
  private void receiveCommittedRequest(CommittedCommand r) {
    if (!this.executionManager.submitCommittedRequest(r)) {
      logger.error("Could not add committed request to executionManager due to maximum capacity.");
    }
  }

  public void sendClientReply(OrderedClientRequest originalRequest, OrderedClientReply reply) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(reply);
      oos.flush();
      var replyBytes = bos.toByteArray();

      // The client is able to connect this sent reply to its original request by using the
      // clientLocalTimestamp. Because it is contained in the request that is propagated by the
      // coordinator in the initial DepPropose, every replica knows the clientLocalTimestamp and
      // use it as the sequenceNumber of the ClientMessageWrapper.
      logger.debug("Send reply {} to client {}", reply, originalRequest.clientId());
      this.scs.sendToClients(
          new int[] {originalRequest.clientId()},
          new ClientMessageWrapper(
              this.ownReplicaId.value(), originalRequest.clientLocalTimestamp(), replyBytes));

    } catch (IOException e) {
      logger.error("Failed to serialize OrderedClientReply for client response", e);
    }
  }

  @Override
  public void processData(SystemMessage sm) {
    this.agrSlotManager.handleReplicaMessage(sm);
  }

  @Override
  public void verifyPending() {}
}
