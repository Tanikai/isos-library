package isos.consensus.dependency;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.CommittedCommand;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestContainer;

/**
 * The ConflictChecker interface is used to get the compact dependency set of a request. In the
 * compact dependency set, only the direct
 */
public interface ConflictChecker {

  /**
   * This function has to be thread-safe.
   *
   * @param slot
   * @param r
   * @param deps Dependencies that were calculated with {@link
   *     #getCompactDependencySet(OrderedClientRequest)}
   */
  void addClientRequest(SequenceNumber slot, ClientRequestContainer r, DependencySet deps);

  /**
   * Required during a view change, when a NewView message is received and a request is overwritten
   * by another request (or the same one) in the same agreement slot.
   *
   * <p>The dependencies are updated with {@link #updateCommitedRequest(CommittedCommand)} when the
   * request is committed after the view change(s).
   *
   * @param slot
   * @param r
   */
  void overwriteClientRequest(SequenceNumber slot, ClientRequestContainer r);

  /**
   * When a request gets committed, its dependency set can change. Thus, we need a separate function
   * that is called a second time after a request has been initially added.
   *
   * @param c The committed request including the sequence number and final, committed dependency
   *     set.
   */
  void updateCommitedRequest(CommittedCommand c);

  /**
   * Requirement: The coordinator [...] computes the dependency set [...] with request r. Method:
   * Iterate over all requests with r, check with predicate `conflict(a, b)`, add SequenceNumber to
   * dependency set if true.
   *
   * <p>Returns the conflicts of a given request as a DependencySet. To keep the dependency sets
   * small, it only returns the direct conflicts to this request. This is called the compact
   * dependency set.
   *
   * <p>This function has to be thread-safe, as it can be called by multiple
   * AgreementSlotQueueProcessors.
   *
   * <p>When SequenceNumber seqNum and OrderedClientRequest r is passed, it is not persisted in the
   * ConflictChecker.
   *
   * @return
   */
  DependencySet getCompactDependencySet(SequenceNumber seqNum, ClientRequestContainer r);
}
