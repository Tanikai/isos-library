package isos.graph;

import isos.consensus.DependencySet;
import isos.message.OrderedClientRequest;

/**
 * Pseudocode line 66
 */
@FunctionalInterface
public interface RequestConflictChecker {
  DependencySet conflicts (OrderedClientRequest r);
}
