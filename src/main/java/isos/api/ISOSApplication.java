package isos.api;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.configuration.ConfigurationManager;
import isos.consensus.AgreementSlotManager;
import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.consensus.model.TimeoutConfiguration;
import isos.execution.ExecuteInApplication;
import isos.execution.CommittedCommand;
import isos.execution.ExecutionManager;
import isos.execution.graph.builder.TrivialDependencyGraphBuilder;
import isos.message.client.OrderedClientRequest;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * DECISION Kai: Maybe interface instead of class? This class is used as the central manager of the
 * replica-side state and logic. -> Use class as baseline, and let users extend certain parts of
 * logic via inheritance, or pass lambda functions to the constructor.
 */
public class ISOSApplication {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  // FIXME Kai: Make timeout value configurable
  private final TimeoutConfiguration timeoutConf = new TimeoutConfiguration(1000);
  private final ReplicaId ownReplicaId;

  /**
   * DECISION Kai: AgreementSlotSequence that contains slots of all replicas, or
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
  private ServerCommunicationSystem scs;

  private final ConfigurationManager configManager;
  private final ExecutionManager executionManager;
  private final Thread executionManagerThread;

  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  private BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public ISOSApplication(
      ConfigurationManager configManager,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict,
      ExecuteInApplication executor) {
    this.configManager = configManager;
    this.applicationConflict = applicationConflict;
    this.ownReplicaId = new ReplicaId(configManager.getStaticConf().getProcessId());

    // FIXME Kai: there should not be this cyclic dependency with the AgreementSlotManager and SCS
    var maxFaults = configManager.getStaticConf().getF();
    var replicaCount = configManager.getStaticConf().getN();
    this.agrSlotManager =
        new AgreementSlotManager(
            ownReplicaId,
            timeoutConf,
            configManager.getStaticConf().getInitialViewAsReplicaId(),
            this::conflicts,
            this::receiveCommittedRequest,
            maxFaults,
            replicaCount);
    try {
      this.scs = new ServerCommunicationSystem(configManager, this.agrSlotManager);
      this.scs.setRequestReceiver(this.agrSlotManager);
    } catch (Exception e) {
      // FIXME Kai: Handle exception (or just remove exception from constructor)
    }
    this.agrSlotManager.initialize(scs);

    // Request Execution
    this.defaultConflict = (a, b) -> a.clientId() == b.clientId();

    this.executionManager =
        new ExecutionManager(
            50, // TODO Kai: ExecutionWindow should be configurable, and what is a good value for
            // the window?
            new TrivialDependencyGraphBuilder(),
            executor);
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
   * Requirement: The coordinator [...] computes the dependency set [...] with request r. Method:
   * Iterate over all requests with r, check with predicate `conflict(a, b)`, add SequenceNumber to
   * dependency set if true
   *
   * <p>Pseudocode line 66, 67
   *
   * <p>Trivial Implementation
   *
   * <p>This function is in the hot path, so performance is critical here.
   *
   * @return All agreement slots that have a DepPropose message (i.e. non-null)
   */
  private DependencySet conflicts(OrderedClientRequest r) {
    // Requirement: For the dependency set, the coordinator takes all known requests from both its
    // own and other replicas' agreement slots into account (see paper sec. B).

    // TODO Optimization: Evaluate whether fork/join could be applicable here -> might be good for
    // large dependency sets
    // Answer: parallelStream() uses fork/join in background

    Set<SequenceNumber> result =
        agrSlotManager
            .getUsedAgreementSlots()
            // value is List<AgreementSlot>
            .values()
            // allow for parallelStream() as well, as dependencies can be calculated independently
            .parallelStream()
            // turn the Stream<List<AgreementSlot>> into Stream<AgreementSlot>
            .flatMap(Collection::stream)
            // if they conflict, return the sequence number, else return null for "no conflict"
            .map(
                slot -> {
                  if (this.defaultConflict
                      .or(this.applicationConflict)
                      .test(r, slot.getRequest())) {
                    return slot.getSeqNum();
                  } else {
                    return null;
                  }
                })
            .filter(Objects::nonNull) // filter out the "no conflict"s
            .collect(Collectors.toSet());

    // Requirement: To limit the size of the set, the coordinator for each replica only includes
    // the **sequence number** of the latest conflicting request.

    // TODO: How can I get the sequence number of only the last conflicting request?
    // Approach 1: Get all conflicts, then filter out the redundant conflicts
    // Approach 2:

    return new DependencySet(result);
  }

  /**
   * Receive a committed request from the {@link AgreementSlotManager} that can be executed by the
   * execution engine.
   *
   * @param r
   */
  public void receiveCommittedRequest(CommittedCommand r) {
    if (!this.executionManager.submitCommittedRequest(r)) {
      logger.error("Could not add committed request to executionManager due to maximum capacity.");
    }
  }
}
