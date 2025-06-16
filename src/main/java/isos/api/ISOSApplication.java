package isos.api;

import bftsmart.communication.ServerCommunicationSystem;
import isos.consensus.AgreementSlotSequence;
import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.consensus.TimeoutConfiguration;
import isos.message.ClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DECISION Kai: Maybe interface instead of class? This class is used as the central manager of the
 * replica-side state and logic.
 */
public class ISOSApplication {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final TimeoutConfiguration timeoutConf = new TimeoutConfiguration(1000);

  // DECISION Kai: AgreementSlotSequence that contains slots of all replicas, or
  // AgreementSlotSequence per replica?
  private AgreementSlotSequence agreementSlots;

  /**
   * Requirement: To start the fast path, the coordinator selects its agreement slot
   * <p>Pseudocode line 10-19
   *
   * @param r
   */
  private void handleIncomingClientRequest(ClientRequest r) {
    // ClientRequest needs to be immutable after receiving

    // TODO Kai line 11: assert r correctly signed (-> should be done in the Networking Layer)

    SequenceNumber seqNum = this.agreementSlots.getLowestUnusedSequenceNumber();
    Set<SequenceNumber> depSet = conflicts(r);
    Set<ReplicaId> followerSet = null; // get quorum of 2f followers with the lowest latency
    DepProposeMessage dp =
        new DepProposeMessage(
            new ReplicaId(-1),
            seqNum,
            new ReplicaId(-1),
            r.calculateHash(),
            new DependencySet(depSet),
            followerSet); // TODO: create constructor / factory method to create message
    throw new NotImplementedException();
    // TODO Line 17: set step of this agreement slot to proposed
    // TODO Line 18: Broadcast message to all replicas

    // TODO: Notify Listeners that a new Request was handled
    // Listeners include: Other requests that are waiting until a certain condition is fulfilled
    // (e.g., 2f messages received)
  }

  /**
   * Requirement: The coordinator [...] computes the dependency set [...] with request r.
   * Method: Iterate over all requests with r, check with predicate `conflict(a, b)`, add SequenceNumber to dependency set if true
   *
   * <p>Pseudocode line 66, 67
   *
   * <p>Trivial Implementation
   *
   * <p>This function is in the hot path, so performance is critical here.
   *
   * @return All agreement slots that have a DepPropose message (i.e. non-null)
   */
  private Set<SequenceNumber> conflicts(ClientRequest r) {
    // Requirement: For the dependency set, the coordinator takes all known requests from both its
    // own and other replicas' agreement slots into account (see paper sec. B).

    // TODO Optimization: Evaluate whether fork/join could be applicable here -> might be good for
    // large dependency sets
    // Answer: parallelStream() uses fork/join in background

    Set<SequenceNumber> result =
        agreementSlots
            .getAgreementSlots()
            .entrySet()
            .stream() // allow for parallelStream() as well, as dependencies can be calculated
            // independently
            // if they conflict, return the sequence number, else return null for "no conflict"
            .map(slot -> conflict(r, slot.getValue().request()) ? slot.getKey() : null)
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
   * @return
   */
  public boolean conflict(ClientRequest a, ClientRequest b) {
    // Requirement: Requests of the same client are automatically treated as conflicting with each
    // other, independent of their content
    if (a.getSender() == b.getSender()) {
      return true;
    }

    return true;
  }
}
