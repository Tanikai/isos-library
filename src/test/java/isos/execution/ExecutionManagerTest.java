package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.graph.builder.TrivialDependencyGraphBuilder;
import isos.message.client.OrderedClientRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class ExecutionManagerTest {

  @Test
  void testSlotsInExecutionWindow() {
    Set<SequenceNumber> committed =
        new HashSet<>(
            Set.of(new SequenceNumber(0, 1), new SequenceNumber(1, 0), new SequenceNumber(2, 1)));
    Set<SequenceNumber> executed = Set.of(new SequenceNumber(2, 1));
    int executionWindowSize = 3;

    Set<SequenceNumber> expectedSlotsInExecutionWindow =
        Set.of(
            new SequenceNumber(0, 0),
            new SequenceNumber(0, 1),
            new SequenceNumber(0, 2),
            new SequenceNumber(0, 3),
            new SequenceNumber(1, 0),
            new SequenceNumber(1, 1),
            new SequenceNumber(1, 2));

    var actualSlots =
        ExecutionManager.slotsInExecutionWindow(committed, executed, executionWindowSize);

    assertEquals(expectedSlotsInExecutionWindow, actualSlots);
  }

  @Test
  void testExecutionManagerThread() throws InterruptedException {
    var executor = mock(ExecuteInApplication.class);
    var batchProcessingMaxSize = 10;
    var manager =
        new ExecutionManager(
            10, new TrivialDependencyGraphBuilder(), executor, batchProcessingMaxSize);
    Thread managerThread = Thread.ofVirtual().start(manager);

    int clientId = 0;
    byte[] clientCommand = "Hello World!".getBytes();
    long clientTimestamp = 1000;

    var seqNum = new SequenceNumber(0, 0);
    OrderedClientRequest firstRequest =
        new OrderedClientRequest(clientId, clientCommand, clientTimestamp);

    var depSet = new DependencySet(Set.of());

    CommittedCommand committed = new CommittedCommand(seqNum, firstRequest, depSet);

    manager.submitCommittedRequest(committed);

    OrderedClientRequest secondRequest = new OrderedClientRequest(1, clientCommand, 2000);

    var dependencySeqNum = new SequenceNumber(0, 1);
    OrderedClientRequest secondRequestDependency = new OrderedClientRequest(2, clientCommand, 1500);

    // Submit the command and dependency out of order to test
    manager.submitCommittedRequest(
        new CommittedCommand(
            new SequenceNumber(0, 2), secondRequest, new DependencySet(Set.of(dependencySeqNum))));

    Thread.sleep(1000);

    manager.submitCommittedRequest(
        new CommittedCommand(
            dependencySeqNum, secondRequestDependency, new DependencySet(Set.of())));

    var execCaptor = ArgumentCaptor.forClass(OrderedClientRequest.class);
    verify(executor, timeout(500).times(3)).execute(execCaptor.capture());

    var actualValueList = execCaptor.getAllValues();

    assertEquals(firstRequest, actualValueList.getFirst());
    assertEquals(secondRequestDependency, actualValueList.get(1));
    assertEquals(secondRequest, actualValueList.get(2));

    // Stop the thread and wait for join
    managerThread.interrupt();
    try {
      managerThread.join();
    } catch (InterruptedException e) {
      fail();
    }
  }
}
