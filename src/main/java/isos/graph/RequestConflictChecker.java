package isos.graph;

import isos.consensus.model.DependencySet;
import isos.message.client.OrderedClientRequest;

/**
 * Pseudocode line 66
 */
@FunctionalInterface
public interface RequestConflictChecker {
  DependencySet conflicts (OrderedClientRequest r);
}
