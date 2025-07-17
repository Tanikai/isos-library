package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import isos.communication.MessageSender;
import isos.consensus.model.*;
import isos.graph.ExecutableRequestReceiver;
import isos.graph.RequestConflictChecker;
import isos.message.ISOSMessage;
import isos.message.OrderedClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepProposeWithRequest;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

class AgmtSlotQueueProcessorCommitTimeoutTest {

  @Test
  void testDepProposeWithRequestTriggersCommitTimeout() throws Exception {
    // Setup
    SequenceNumber seqNum = new SequenceNumber(1, 0);
    ReplicaId coordinatorId = new ReplicaId(1);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hash123", new DependencySet(), new HashSet<>());
    OrderedClientRequest clientRequest = mock(OrderedClientRequest.class);
    DepProposeWithRequest depProposeWithRequest =
        new DepProposeWithRequest(depPropose, clientRequest);

    AgreementSlot slot = new AgreementSlot(seqNum);

    var incomingQueue = new LinkedBlockingQueue<ISOSMessage>();
    var timeoutConfig = new TimeoutConfiguration(100); // commit timeout is delta * 9 -> 0.9 sec

    MessageSender msgSender = mock(MessageSender.class);

    // Just returns empty conflicts
    RequestConflictChecker conflictChecker = (ClientRequest) -> new DependencySet();

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
            1);

    // Act
    Thread processorThread = new Thread(processor);
    processorThread.start();
    incomingQueue.put(depProposeWithRequest);

    // Assert
    assertEquals(new ViewNumber(-1), slot.getViewNumber());
    Thread.sleep(
        timeoutConfig.getCommitTimeout() + 500); // wait until commit timeout expires + 500 millis
    // after the commit timeout expires, the view number should be increased by 1
    assertEquals(new ViewNumber(0), slot.getViewNumber());
    processorThread.interrupt();
    processorThread.join();
  }
}
