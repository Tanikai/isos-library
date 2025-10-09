package isos.api;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.configuration.ConfigurationManager;
import isos.communication.ClientMessageWrapper;
import isos.consensus.AgreementSlotManager;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.dependency.TrivialConflictChecker;
import isos.consensus.model.TimeoutConfiguration;
import isos.execution.CommittedCommand;
import isos.execution.ExecuteInApplication;
import isos.execution.ExecutionManager;
import isos.execution.graph.ClientPayloadDeserializer;
import isos.execution.graph.DependencyGraphBuilder;
import isos.execution.graph.builder.TrivialDependencyGraphBuilder;
import isos.message.client.OrderedClientReply;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;
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
public class ISOSApplication {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  // FIXME Kai: Timeout value should be dynamic, determined by round trip time
  private final TimeoutConfiguration timeoutConf;
  private final ReplicaId ownReplicaId;

  /**
   * DECIDED Kai: AgreementSlotSequence that contains slots of all replicas, or AgreementSlotSequence
   * per replica? -> All slots of replicas
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

  /**
   * Used to generate the dependency set and determine the strongly connected components for
   * execution.
   */
  private final DependencyGraphBuilder dependencyGraphBuilder;

  private final ExecutionManager executionManager;
  private final Thread executionManagerThread;

  private final ClientPayloadDeserializer deserializer;

  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public ISOSApplication(
      ConfigurationManager configManager,
      ClientPayloadDeserializer deserializer,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict,
      ExecuteInApplication executor) {
    this.configManager = configManager;
    this.timeoutConf =
        new TimeoutConfiguration(
            this.configManager.getStaticConf().getInitialIsosTimeoutDeltaMillis());
    this.deserializer = deserializer;
    this.ownReplicaId = ReplicaId.of(configManager.getStaticConf().getProcessId());

    // Conflicts
    this.defaultConflict = (a, b) -> a.clientId() == b.clientId();
    this.applicationConflict = applicationConflict;
    this.dependencyGraphBuilder = new TrivialDependencyGraphBuilder();
    this.conflictChecker =
        new TrivialConflictChecker(this.defaultConflict, this.applicationConflict);

    var maxFaults = configManager.getStaticConf().getF();
    var replicaCount = configManager.getStaticConf().getN();
    this.agrSlotManager =
        new AgreementSlotManager(
            ownReplicaId,
            timeoutConf,
            configManager.getStaticConf().getInitialViewAsReplicaId(),
            this.conflictChecker,
            this::receiveCommittedRequest,
            deserializer,
            maxFaults,
            replicaCount);
    try {
      this.scs = new ServerCommunicationSystem(configManager, this.agrSlotManager);
      this.scs.setRequestReceiver(this.agrSlotManager);
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not initialize ServerCommunicationSystem: " + e.getMessage());
    }
    this.agrSlotManager.initialize(scs);

    // Request Execution
    this.executionManager =
        new ExecutionManager(
            this.configManager.getStaticConf().getExecutionWindowSize(),
            this.dependencyGraphBuilder,
            executor,
            this.configManager.getStaticConf().getMaxBatchSize());
    this.executionManagerThread = Thread.ofVirtual().start(this.executionManager);
  }

  /** Starts the application by connecting to the replicas first. */
  public void start() {
    this.scs.start();
    logger.info("Wait until other replicas are connected");
    this.scs.waitUntilViewConnected();
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
      logger.info("Send reply {} to client {}", reply, originalRequest.clientId());
      this.scs.sendToClients(
          new int[] {originalRequest.clientId()},
          new ClientMessageWrapper(
              this.ownReplicaId.value(), originalRequest.clientLocalTimestamp(), replyBytes));

    } catch (IOException e) {
      logger.warn("Failed to serialize OrderedClientReply for client response", e);
    }
  }
}
