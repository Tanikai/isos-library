package isos.api;

import com.yahoo.ycsb.Client;
import isos.consensus.AgreementSlotSequence;
import isos.consensus.SequenceNumber;
import isos.message.ClientRequest;
import isos.utils.NotImplementedException;

import java.util.HashSet;
import java.util.Set;

/**
 * DECISION Kai: Maybe interface instead of class? This class is used as the central manager of the
 * replica-side state and logic.
 */
public class ISOSApplication {

  // DECISION Kai: AgreementSlotSequence that contains slots of all replicas, or
  // AgreementSlotSequence per replica?
  private AgreementSlotSequence agreementSlots;

  /**
   * Requirement: To start the fast path, the coordinator selects its agreement slot
   *
   * @param r
   */
  private void handleIncomingRequest(ClientRequest r) {
    // ClientRequest needs to be immutable after receiving

    // TODO: Notify Listeners that a new Request was handled
    // Listeners include: Other request that are waiting until a certain condition is fulfilled
    // (e.g., 2f messages received)

    throw new NotImplementedException();
  }

  /**
   * Requirement: The coordinator [...] computes the dependency set [...] with request r.
   *
   * <p>This function is in the hot path, so performance is critical here.
   *
   * @return
   */
  private Set<SequenceNumber> getDependencySet(ClientRequest r) {
    // iterate over all requests with r, check with predicate `conflict(a, b)`, add to result
    // if true
    Set<SequenceNumber> deps = new HashSet<>();

    // Requirement: For the dependency set, the coordinator takes all known requests from both its
    // own and other replicas' agreement slots into account (see paper sec. B).

    // TODO: Evaluate whether fork/join could be applicable here -> might be good for large
    // dependency sets

    //    for (var slot : this.agreementSlots.) {
    //    }

    // Requirement: To limit the size of the set, the coordinator for each replica only includes
    // the **sequence number** of the latest conflicting request.

    return deps;
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
