package isos.consensus.dependency;

import isos.execution.scc.SccFinder;
import isos.message.replica.ClientRequestContainer;

import java.util.function.BiPredicate;

public class ConflictCheckerFactory {

  public static ConflictChecker createConflictChecker(
      CompactDepSetStrategy strategy,
      SccFinder sccFinder,
      BiPredicate<ClientRequestContainer, ClientRequestContainer> defaultConflict,
  BiPredicate<ClientRequestContainer, ClientRequestContainer> applicationConflict)
  {
    return switch (strategy) {
      case TRIVIAL -> new TrivialConflictChecker(sccFinder, defaultConflict, applicationConflict);
      case HIGHEST_PER_REPLICA -> new HighestConflictEachReplicaChecker(defaultConflict, applicationConflict);
    };
  }
}
