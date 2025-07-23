package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import isos.communication.MessageSender;
import isos.consensus.model.*;
import isos.graph.ExecutableRequestReceiver;
import isos.graph.ExecuteMessage;
import isos.graph.RequestConflictChecker;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.fast.DepCommitMessage;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgmtSlotQueueProcessorTest {

  private TimeoutConfiguration timeoutConfig;
  private MessageSender msgSenderMock;
  private BlockingQueue<ISOSMessage> incomingQueue;
  private DependencyWaitFunction dependencyWaitMock;
  private ExecutableRequestReceiver requestExecutorMock;
  private int maxFaults = 1;

  @BeforeEach
  void setUp() {
    // when debugging, increase the Timeout delta
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
  void testCoordinatorFastPath() {
    var ownReplicaId = new ReplicaId(2);
    var otherReplicaIds = new ReplicaId[] {new ReplicaId(1), new ReplicaId(0), new ReplicaId(3)};
    // Act
    var seqNum = new SequenceNumber(ownReplicaId, 1);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();

    RequestConflictChecker conflictChecker =
        (r) -> new DependencySet(List.of(new SequenceNumber(ownReplicaId, 0)));

    // by initially setting a clientRequest, we communicate to the Queue Processor that it is the
    // coordinator
    var slot = new AgreementSlot(seqNum, clientRequest);

    when(msgSenderMock.getLowestPingReplicas(anyInt()))
        .thenReturn(new HashSet<>(List.of(new ReplicaId(0), new ReplicaId(3))));

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

    var argumentCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    // Because the queueProcessor is in another thread, we use timeout to wait until the function is
    // called
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(false), argumentCaptor.capture());

    // When the queueProcessor handles the clientRequest, it should broadcast the DepPropose and
    // client request
    var wrapper = argumentCaptor.getValue();
    var depProposeWithRequest = (DepProposeWithRequest) wrapper.getPayload();
    var depPropose = depProposeWithRequest.depPropose();
    assertEquals(seqNum, depProposeWithRequest.seqNum());
    assertEquals(ownReplicaId.value(), wrapper.getSender());
    assertEquals(seqNum, depPropose.seqNum());
    assertEquals(ownReplicaId, depPropose.coordinatorId());
    assertEquals(clientRequestHash, depPropose.requestHash());
    assertEquals(
        new DependencySet(List.of(new SequenceNumber(ownReplicaId, 0))), depPropose.depSet());
    assertEquals(Set.of(new ReplicaId(3), new ReplicaId(0)), depPropose.followerQuorum());

    // When other replicas receive the DepPropose message, they calculate their dependencies and
    // send a DepVerify message
    List<DepVerifyMessage> replies = new LinkedList<>();
    var depProposeHash = depPropose.calculateHash();

    // f+1 Replicas reply with an additional dependency, thus it has to be included in the DepCommit
    // The original dependency is included in all replies
    DependencySet finalDepSet =
        new DependencySet(List.of(new SequenceNumber(ownReplicaId, 0), new SequenceNumber(0, 0)));

    // Only the replicas in the follower quorum send a DepVerify message
    for (var r : depPropose.followerQuorum()) {
      replies.add(new DepVerifyMessage(seqNum, r, depProposeHash, finalDepSet));
    }
    var depVerifiesHash = DepVerifyMessage.calculateDepVerifyHash(replies);

    // Send replies to our agreement slot
    incomingQueue.addAll(replies);

    // Now we expect a DepCommit message that is sent
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), argumentCaptor.capture());

    wrapper = argumentCaptor.getValue();
    var depCommit = (DepCommitMessage) wrapper.getPayload();
    incomingQueue.add(depCommit);
    // as broadcast is called with includeSelf true, we have to add it to the incomingQueue

    assertEquals(seqNum, depCommit.seqNum());
    assertEquals(ownReplicaId, depCommit.replicaId());
    assertEquals(depVerifiesHash, depCommit.depVerifiesHash());

    // Replica has to receive 2f+1 matching DepCommits (including itself) -> send 2 DepCommits from
    // other followers
    DepCommitMessage depCommit1 = new DepCommitMessage(seqNum, otherReplicaIds[0], depVerifiesHash);
    DepCommitMessage depCommit2 = new DepCommitMessage(seqNum, otherReplicaIds[1], depVerifiesHash);

    incomingQueue.add(depCommit1);
    incomingQueue.add(depCommit2);

    var execArgCaptor = ArgumentCaptor.forClass(ExecuteMessage.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execArgCaptor.capture());
    var execMessage = execArgCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(finalDepSet, execMessage.depSet());

    // After a request has been forwarded to execution, we are done!
  }

  @Test
  void testCoordinatorReconciliationPath() {}

  @Test
  void testFollowerHappyPath() {
    var coordinatorId = new ReplicaId(0);

    var ownReplicaId = new ReplicaId(1);
    var otherReplicaIds = new ReplicaId[] {new ReplicaId(2), new ReplicaId(0), new ReplicaId(3)};

    var otherFollowerId = new ReplicaId(3);
    var seqNum = new SequenceNumber(coordinatorId, 1);

    var depSet = new DependencySet(List.of(new SequenceNumber(0, 0)));
    var followerQuorum = Set.of(ownReplicaId, otherFollowerId);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();
    var depPropose =
        new DepProposeMessage(seqNum, coordinatorId, clientRequestHash, depSet, followerQuorum);
    var depProposeHash = depPropose.calculateHash();
    var depProposeWithRequest = new DepProposeWithRequest(depPropose, clientRequest);

    RequestConflictChecker conflictChecker =
        (r) -> new DependencySet(List.of(new SequenceNumber(0, 0), new SequenceNumber(1, 0)));
    // We have 1 dependencySet with 0.0 and 2 with 0.0+1.0

    var slot = new AgreementSlot(seqNum);
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

    // Follower is in FollowerQuorum, so we have to broadcast a DepVerify
    incomingQueue.add(depProposeWithRequest);

    ArgumentCaptor<ISOSMessageWrapper> argCaptor =
        ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    // DepVerify
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), argCaptor.capture());

    var wrapper = argCaptor.getValue();
    var depVerify = (DepVerifyMessage) wrapper.getPayload();
    incomingQueue.add(depVerify);
    assertEquals(seqNum, depVerify.seqNum());
    assertEquals(ownReplicaId, depVerify.followerId());
    assertEquals(depProposeHash, depVerify.depProposeHash());
    assertEquals(
        new DependencySet(List.of(new SequenceNumber(0, 0), new SequenceNumber(1, 0))),
        depVerify.depSet());

    // We still have to receive the second DepVerify from the other replica
    var otherDepVerify =
        new DepVerifyMessage(
            seqNum,
            new ReplicaId(3),
            depProposeHash,
            new DependencySet(List.of(new SequenceNumber(0, 0), new SequenceNumber(1, 0))));
    incomingQueue.add(otherDepVerify);

    var depVerifiesHash =
        DepVerifyMessage.calculateDepVerifyHash(List.of(otherDepVerify, depVerify));
    var depVerifiesHashSwapped =
        DepVerifyMessage.calculateDepVerifyHash(List.of(depVerify, otherDepVerify));
    assertEquals(depVerifiesHash, depVerifiesHashSwapped);

    // With the DepPropose, own DepVerify, and DepVerify from other replica with matching
    // Dependencies, the follower broadcasts a DepCommit message
    verify(msgSenderMock, timeout(500).times(2)).broadcastToReplicas(eq(true), argCaptor.capture());

    wrapper = argCaptor.getValue();
    var depCommit = (DepCommitMessage) wrapper.getPayload();
    incomingQueue.add(depCommit);
    assertEquals(seqNum, depCommit.seqNum());
    assertEquals(ownReplicaId, depCommit.replicaId());
    assertEquals(depVerifiesHash, depCommit.depVerifiesHash());

    // Create the remaining 2f depCommit messages
    var depCommit1 = new DepCommitMessage(seqNum, coordinatorId, depVerifiesHash);
    var depCommit2 = new DepCommitMessage(seqNum, otherFollowerId, depVerifiesHash);
    incomingQueue.add(depCommit1);
    incomingQueue.add(depCommit2);

    var execArgCaptor = ArgumentCaptor.forClass(ExecuteMessage.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execArgCaptor.capture());
    var execMessage = execArgCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(
        new DependencySet(List.of(new SequenceNumber(0, 0), new SequenceNumber(1, 0))),
        execMessage.depSet());
  }

  @Test
  void testFollowerReconciliationPath() {}

  /** A replica that is not included in the followerQuorum of the DepPropose message. */
  @Test
  void testReplicaFastPath() {}
}
