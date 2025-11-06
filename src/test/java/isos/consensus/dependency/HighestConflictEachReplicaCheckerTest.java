package isos.consensus.dependency;

import isos.benchmark.kvstore.model.KVMessage;
import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.Dependency;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestBatch;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HighestConflictEachReplicaCheckerTest {

  @Test
  void getCompactDependencySet() {

    var conflictChecker =
        new HighestConflictEachReplicaChecker(
            (a, b) -> {
              // we assume that all batches have exactly 1 request
              return a.getRequests()[0].clientId() == b.getRequests()[0].clientId();
            },
            (a, b) -> {
              return false; // only clientid conflicts
            });

    var firstRequest = new SequenceNumber(0, 0);
    conflictChecker.addClientRequest(
        firstRequest,
        new ClientRequestBatch(Set.of(new OrderedClientRequest(0, new byte[0], 0))),
        new DependencySet());

    var second = new SequenceNumber(0, 1);
    var deps =
        conflictChecker.getCompactDependencySet(
            second, new ClientRequestBatch(Set.of(new OrderedClientRequest(0, new byte[0], 1))));
    assertEquals(Set.of(firstRequest), deps.dependencies());
  }
}
