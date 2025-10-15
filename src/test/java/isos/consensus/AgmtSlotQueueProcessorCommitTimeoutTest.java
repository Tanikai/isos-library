package isos.consensus;

import isos.communication.MessageSender;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.model.*;
import isos.execution.ExecutableRequestReceiver;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AgmtSlotQueueProcessorCommitTimeoutTest {

  @Test
  void testDepProposeWithRequestTriggersCommitTimeout() throws Exception {
    // Setup
    SequenceNumber seqNum = SequenceNumber.of(1, 0);
    ReplicaId coordinatorId = ReplicaId.of(1);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hash123", new DependencySet(), new HashSet<>());
    OrderedClientRequest clientRequest = mock(OrderedClientRequest.class);
    DepProposeWithRequest depProposeWithRequest =
        new DepProposeWithRequest(depPropose, clientRequest);

    AgreementSlot slot = new AgreementSlot(seqNum);

    var incomingQueue = new LinkedBlockingDeque<ISOSMessage>();
    var timeoutConfig = new TimeoutConfiguration(100); // commit timeout is delta * 9 -> 0.9 sec

    MessageSender msgSender = mock(MessageSender.class);

    // Just returns empty conflicts
    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any())).thenReturn(new DependencySet());

    DependencyWaitFunction dependencyWait = mock(DependencyWaitFunction.class);
    doNothing().when(dependencyWait).waitUntilConsensusStarted(any());

    ExecutableRequestReceiver executeReceiver = mock(ExecutableRequestReceiver.class);
    doNothing().when(executeReceiver).forwardRequestToExecution(any());

    AgmtSlotQueueProcessor processor =
        new AgmtSlotQueueProcessor(
            coordinatorId,
            seqNum,
            incomingQueue,
            timeoutConfig,
            msgSender,
            slot,
            conflictChecker,
            dependencyWait,
            executeReceiver,
            1,
            4);

    // Act
    Thread processorThread = new Thread(processor);
    processorThread.start();
    incomingQueue.put(depProposeWithRequest);

    // Assert
    assertEquals(ViewNumber.of(-1), slot.getViewNumber());
    Thread.sleep(
        timeoutConfig.getCommitTimeout() + 500); // wait until commit timeout expires + 500 millis
    // after the commit timeout expires, the view number should be increased by 1
    assertEquals(ViewNumber.of(0), slot.getViewNumber());
    processorThread.interrupt();
    processorThread.join();
  }
}
