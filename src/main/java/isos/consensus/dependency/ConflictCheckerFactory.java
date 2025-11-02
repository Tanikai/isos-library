package isos.consensus.dependency;

import isos.execution.scc.SccFinder;
import isos.message.client.OrderedClientRequest;

import java.util.function.BiPredicate;

public class ConflictCheckerFactory {

  public static ConflictChecker createConflictChecker(
      CompactDepSetStrategy strategy,
      SccFinder sccFinder,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict,
  BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict) 
  {
    return switch (strategy) {
      case TRIVIAL -> new TrivialConflictChecker(sccFinder, defaultConflict, applicationConflict);
      case HIGHEST_PER_REPLICA -> new HighestConflictEachReplicaChecker(defaultConflict, applicationConflict);
    };
  }
}
