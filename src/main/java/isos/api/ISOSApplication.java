package isos.api;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.configuration.ConfigurationManager;
import isos.consensus.*;
import isos.message.OrderedClientRequest;
import isos.utils.ReplicaId;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DECISION Kai: Maybe interface instead of class? This class is used as the central manager of the
 * replica-side state and logic.
 */
public class ISOSApplication {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final TimeoutConfiguration timeoutConf = new TimeoutConfiguration(1000);
  private final ReplicaId ownReplicaId;

  // DECISION Kai: AgreementSlotSequence that contains slots of all replicas, or
  // AgreementSlotSequence per replica?
  private final AgreementSlotManager agrSlotManager;

  /**
   * Send and receive messages with scs.
   *
   * <ul>
   *   <li>MessageHandler: Callback for messages received from other replicas.
   *   <li>requestReceiver: Callback for requests from clients.
   * </ul>
   */
  private ServerCommunicationSystem scs;

  private final ConfigurationManager configManager;

  private final BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  private BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  public ISOSApplication(ConfigurationManager configManager) {
    this.configManager = configManager;
    this.ownReplicaId = new ReplicaId(configManager.getStaticConf().getProcessId());

    // FIXME Kai: there should not be this cyclic dependency with the AgreementSlotManager and SCS
    this.agrSlotManager =
        new AgreementSlotManager(
            ownReplicaId, timeoutConf, configManager.getStaticConf().getInitialViewAsReplicaId());
    try {
      this.scs = new ServerCommunicationSystem(configManager, this.agrSlotManager);
      // TODO: Add Request Receiver
      //      this.scs.setRequestReceiver();
    } catch (Exception e) {
      // FIXME Kai: Handle exception (or just remove exception from constructor)
    }
    this.agrSlotManager.initialize(scs);

    // Request
    this.defaultConflict = (a, b) -> a.clientId() == b.clientId();
  }

  /** Starts the application by connecting to the repliacs first. */
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
  private Set<SequenceNumber> conflicts(OrderedClientRequest r) {
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
            .stream()
            // turn the Stream<List<AgreementSlot>> into Stream<AgreementSlot>
            .flatMap(Collection::stream)
            // if they conflict, return the sequence number, else return null for "no conflict"
            .map(slot -> conflict(r, slot.getRequest()) ? slot.getSeqNum() : null)
            .filter(Objects::nonNull) // filter out the "no conflict"s
            .collect(Collectors.toSet());

    // Requirement: To limit the size of the set, the coordinator for each replica only includes
    // the **sequence number** of the latest conflicting request.

    // TODO: How can I get the sequence number of only the last conflicting request?
    // Approach 1: Get all conflicts, then filter out the redundant conflicts
    // Approach 2:

    return result;
  }

  /**
   * Has to be overwritten by the application developer. Requires application-specific information
   * whether two requests conflict with each other or not (e.g., writes to the same key in a
   * KV-store)
   *
   * @param a
   * @param b
   * @return @Deprecated (?) See BiPredicate applicationConflict.
   */
  public boolean conflict(OrderedClientRequest a, OrderedClientRequest b) {
    // Requirement: Requests of the same client are automatically treated as conflicting with each
    // other, independent of their content
    if (a.clientId() == b.clientId()) {
      return true;
    }

    return true;
  }
}
