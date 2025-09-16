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
    Set<SequenceNumber> committed = new HashSet<>(Set.of(new SequenceNumber(0, 0)));
    Set<SequenceNumber> executed = new HashSet<>();
    int executionWindowSize = 10;

    Set<SequenceNumber> expectedSlotsInExecutionWindow = Set.of(new SequenceNumber(0, 0));

    var actualSlots =
        ExecutionManager.slotsInExecutionWindow(committed, executed, executionWindowSize);

    assertEquals(expectedSlotsInExecutionWindow, actualSlots);
  }

  @Test
  void testExecutionManagerThread() {
    var executor = mock(ExecuteInApplication.class);
    var batchProcessingMaxSize = 10;
    var manager =
        new ExecutionManager(
            10, new TrivialDependencyGraphBuilder(), executor, batchProcessingMaxSize);
    Thread managerThread = Thread.ofVirtual().start(manager);

    int clientId = 0;
    byte[] clientCommand = "Hello World!".getBytes();
    long clientTimestamp = 1000;

    var seqNum = new SequenceNumber(0, 25);
    OrderedClientRequest clientRequest =
        new OrderedClientRequest(clientId, clientCommand, clientTimestamp);

    var depSet = new DependencySet(Set.of());

    CommittedCommand committed = new CommittedCommand(seqNum, clientRequest, depSet);

    manager.submitCommittedRequest(committed);

    // After a single command has been submitted, it should be executed.
    var execCaptor = ArgumentCaptor.forClass(OrderedClientRequest.class);
    verify(executor, timeout(500)).execute(execCaptor.capture());
    verify(executor, calls(1));
    OrderedClientRequest actualRequest = execCaptor.getValue();
    assertEquals(clientRequest, actualRequest);

    // Stop the thread and wait for join
    managerThread.interrupt();
    try {
      managerThread.join();
    } catch (InterruptedException e) {
      fail();
    }
  }
}
