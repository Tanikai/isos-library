package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.ExecutionUtils;
import isos.execution.graph.builder.TrivialDependencyGraphBuilder;
import isos.execution.graph.optimizations.CachedDependencyGraphBuilder;
import isos.execution.manager.ExecutionManager;
import isos.execution.scc.TarjanSCC;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestBatch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class ExecutionManagerTest {

  @Test
  void testExecutedAndExecutionWindowSlots() {
    Set<SequenceNumber> committed =
        new HashSet<>(
            Set.of(
                SequenceNumber.of(0, 1), //
                SequenceNumber.of(1, 0),
                SequenceNumber.of(2, 1)));
    Set<SequenceNumber> executed = Set.of(SequenceNumber.of(2, 1));
    int expansionLimitSize = 3;

    Set<SequenceNumber> expectedSlotsInExecutionWindow =
        Set.of(
            SequenceNumber.of(0, 0),
            SequenceNumber.of(0, 1),
            SequenceNumber.of(0, 2),
            SequenceNumber.of(0, 3),
            SequenceNumber.of(1, 0),
            SequenceNumber.of(1, 1),
            SequenceNumber.of(1, 2),
            SequenceNumber.of(2, 0),
            SequenceNumber.of(2, 1),
            SequenceNumber.of(2, 2),
            SequenceNumber.of(2, 3));

    var actualSlots =
        ExecutionUtils.executedAndExecutionWindowSlots(committed, executed, expansionLimitSize);

    assertEquals(
        expectedSlotsInExecutionWindow.stream().sorted().toList(),
        actualSlots.stream().sorted().toList());
  }

  @Test
  void testExecutionManagerTrivialDepGraph() throws InterruptedException {
    var executor = mock(ExecuteInApplication.class);
    var expansionLimitSize = 10;
    var batchProcessingMaxSize = 10;
    var sccFinder = new TarjanSCC();
    var manager =
        new ExecutionManager(
            new TrivialDependencyGraphBuilder(expansionLimitSize),
            sccFinder,
            executor,
            batchProcessingMaxSize);
    Thread managerThread = Thread.ofVirtual().start(manager);

    int clientId = 0;
    byte[] clientCommand = "Hello World!".getBytes();
    long clientTimestamp = 1000;

    var seqNum = SequenceNumber.of(0, 0);
    OrderedClientRequest firstRequest =
        new OrderedClientRequest(clientId, clientCommand, clientTimestamp);
    var firstContainer = new ClientRequestBatch(List.of(firstRequest));

    var depSet = new DependencySet(Set.of());

    CommittedCommand committed = new CommittedCommand(seqNum, firstContainer, depSet);

    manager.submitCommittedRequest(committed);

    OrderedClientRequest secondRequest = new OrderedClientRequest(1, clientCommand, 2000);
    var secondContainer = new ClientRequestBatch(List.of(secondRequest));

    var dependencySeqNum = SequenceNumber.of(0, 1);
    OrderedClientRequest secondRequestDependency = new OrderedClientRequest(2, clientCommand, 1500);
    var secondDependencyContainer = new ClientRequestBatch(List.of(secondRequestDependency));

    // Submit the command and dependency out of order to test
    manager.submitCommittedRequest(
        new CommittedCommand(
            SequenceNumber.of(0, 2), secondContainer, new DependencySet(Set.of(dependencySeqNum))));

    Thread.sleep(1000);

    manager.submitCommittedRequest(
        new CommittedCommand(
            dependencySeqNum, secondDependencyContainer, new DependencySet(Set.of())));

    var execCaptor = ArgumentCaptor.forClass(ClientRequestBatch.class);
    verify(executor, timeout(500).times(3)).execute(execCaptor.capture());

    var actualValueList = execCaptor.getAllValues();

    assertEquals(firstContainer, actualValueList.getFirst()); // SeqNum 0.0
    assertEquals(secondDependencyContainer, actualValueList.get(1)); // SeqNum 0.1
    assertEquals(secondContainer, actualValueList.get(2)); // SeqNum 0.2

    // Stop the thread and wait for join
    managerThread.interrupt();
    try {
      managerThread.join();
    } catch (InterruptedException e) {
      fail();
    }
  }

  @Test
  void testExecutionManagerCachedDepGraph() throws InterruptedException {
    var executor = mock(ExecuteInApplication.class);
    var expansionLimitSize = 10;
    var batchProcessingMaxSize = 10;
    var sccFinder = new TarjanSCC();
    var manager =
        new ExecutionManager(
            new CachedDependencyGraphBuilder(expansionLimitSize),
            sccFinder,
            executor,
            batchProcessingMaxSize);
    Thread managerThread = Thread.ofVirtual().start(manager);

    int clientId = 0;
    byte[] clientCommand = "Hello World!".getBytes();
    long clientTimestamp = 1000;

    var seqNum = SequenceNumber.of(0, 0);
    OrderedClientRequest firstRequest =
        new OrderedClientRequest(clientId, clientCommand, clientTimestamp);
    var firstContainer = new ClientRequestBatch(List.of(firstRequest));

    var depSet = new DependencySet(Set.of());

    CommittedCommand committed = new CommittedCommand(seqNum, firstContainer, depSet);

    manager.submitCommittedRequest(committed);

    OrderedClientRequest secondRequest = new OrderedClientRequest(1, clientCommand, 2000);
    var secondContainer = new ClientRequestBatch(List.of(secondRequest));

    var dependencySeqNum = SequenceNumber.of(0, 1);
    OrderedClientRequest secondRequestDependency = new OrderedClientRequest(2, clientCommand, 1500);
    var secondDependencyContainer = new ClientRequestBatch(List.of(secondRequestDependency));

    // Submit the command and dependency out of order to test
    manager.submitCommittedRequest(
        new CommittedCommand(
            SequenceNumber.of(0, 2), secondContainer, new DependencySet(Set.of(dependencySeqNum))));

    Thread.sleep(1000);

    manager.submitCommittedRequest(
        new CommittedCommand(
            dependencySeqNum, secondDependencyContainer, new DependencySet(Set.of())));

    var execCaptor = ArgumentCaptor.forClass(ClientRequestBatch.class);
    verify(executor, timeout(500).times(3)).execute(execCaptor.capture());

    var actualValueList = execCaptor.getAllValues();

    assertEquals(firstContainer, actualValueList.getFirst()); // SeqNum 0.0
    assertEquals(secondDependencyContainer, actualValueList.get(1)); // SeqNum 0.1
    assertEquals(secondContainer, actualValueList.get(2)); // SeqNum 0.2

    // Stop the thread and wait for join
    managerThread.interrupt();
    try {
      managerThread.join();
    } catch (InterruptedException e) {
      fail();
    }
  }
}
