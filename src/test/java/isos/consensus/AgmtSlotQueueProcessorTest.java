package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import isos.communication.MessageSender;
import isos.consensus.model.*;
import isos.graph.ExecutableRequestReceiver;
import isos.graph.RequestConflictChecker;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.utils.ReplicaId;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgmtSlotQueueProcessorTest {

  //
  private ReplicaId ownReplicaId;
  private ReplicaId[] otherReplicaIds;
  private TimeoutConfiguration timeoutConfig;
  private MessageSender msgSenderMock;
  private BlockingQueue<ISOSMessage> incomingQueue;
  private DependencyWaitFunction dependencyWaitMock;
  private ExecutableRequestReceiver requestExecutorMock;
  private int maxFaults = 1;

  @BeforeEach
  void setUp() {
    ownReplicaId = new ReplicaId(2);
    otherReplicaIds = new ReplicaId[] {new ReplicaId(1), new ReplicaId(0), new ReplicaId(3)};
    timeoutConfig = new TimeoutConfiguration(1000);
    msgSenderMock = mock(MessageSender.class);
    incomingQueue = new LinkedBlockingQueue<>();
    dependencyWaitMock = mock(DependencyWaitFunction.class);
    requestExecutorMock = mock(ExecutableRequestReceiver.class);
  }

  /**
   * Tests the case where the queue processor is the coordinator and the fast path is successful
   * (i.e., the correct messages are received)
   */
  @Test
  void testQueueProcessorCoordinatorFastPath() {
    // Act
    var seqNum = new SequenceNumber(ownReplicaId, 1);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();

    RequestConflictChecker conflictChecker =
        (r) -> new DependencySet(List.of(new SequenceNumber(ownReplicaId, 0)));

    var slot = new AgreementSlot(seqNum, clientRequest);
    var queueProcessor =
        new AgmtSlotQueueProcessor(
            ownReplicaId,
            seqNum,
            incomingQueue,
            timeoutConfig,
            msgSenderMock,
            slot,
            conflictChecker,
            dependencyWaitMock,
            requestExecutorMock,
            maxFaults);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    // When the queueProcessor handles the clientRequest, it should broadcast the DepPropose and
    // client request

    // Because the queueProcessor is in another thread, we use timeout to wait until the function is
    // called
    verify(msgSenderMock, timeout(500))
        .broadcastToReplicas(
            eq(false),
            argThat(
                msg -> {
                  var wrapper = (ISOSMessageWrapper) msg;
                  var depProposeWithRequest = (DepProposeWithRequest) wrapper.getPayload();
                  var depPropose = depProposeWithRequest.depPropose();
                  return seqNum.equals(depProposeWithRequest.seqNum())
                      && ownReplicaId.value() == wrapper.getSender()
                      && seqNum.equals(depPropose.seqNum())
                      && ownReplicaId.equals(depPropose.coordinatorId())
                      && clientRequestHash.equals(depPropose.requestHash())
                      && new DependencySet(List.of(new SequenceNumber(ownReplicaId, 0)))
                          .equals(depPropose.depSet());
                }));

    // When other replicas receive the DepPropose message, they calculate their dependencies and
    // send a DepVerify message
  }

  @Test
  void testQueueProcessorReconciliationPath() {}
}
