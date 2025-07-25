package isos.execution.graph;

import isos.consensus.model.DependencySet;
import isos.message.client.OrderedClientRequest;

/** Pseudocode line 66 */
@FunctionalInterface
public interface RequestConflictChecker {
  /**
   * Returns the conflicts of a given request as a DependencySet. To keep the dependency sets small,
   * it only returns the direct conflicts to this request.
   * @param r
   * @return
   */
  DependencySet conflicts(OrderedClientRequest r);
}
