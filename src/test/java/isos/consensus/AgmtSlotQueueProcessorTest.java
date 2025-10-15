package isos.consensus;

import isos.communication.MessageSender;
import isos.consensus.dependency.ConflictChecker;
import isos.consensus.model.*;
import isos.execution.CommittedCommand;
import isos.execution.ExecutableRequestReceiver;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.fast.DepCommitMessage;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.reconciliation.CommitMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgmtSlotQueueProcessorTest {

  private TimeoutConfiguration timeoutConfig;
  private MessageSender msgSenderMock;
  private BlockingDeque<ISOSMessage> incomingQueue;
  private DependencyWaitFunction dependencyWaitMock;
  private ExecutableRequestReceiver requestExecutorMock;
  private int maxFaults = 1;
  private int replicaCount = 4;

  @BeforeEach
  void setUp() {
    // when debugging, increase the Timeout delta
    timeoutConfig = new TimeoutConfiguration(1000);
    msgSenderMock = mock(MessageSender.class);
    incomingQueue = new LinkedBlockingDeque<>();
    dependencyWaitMock = mock(DependencyWaitFunction.class);
    requestExecutorMock = mock(ExecutableRequestReceiver.class);
  }

  /**
   * Tests the case where the queue processor is the coordinator and the fast path is successful
   * (i.e., the correct messages are received)
   */
  @Test
  void testCoordinatorFastPath() {
    var ownReplicaId = ReplicaId.of(2);
    var otherReplicaIds = new ReplicaId[] {ReplicaId.of(1), ReplicaId.of(0), ReplicaId.of(3)};
    // Act
    var seqNum = SequenceNumber.of(ownReplicaId, 1);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();

    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any()))
        .thenReturn(new DependencySet(SequenceNumber.of(ownReplicaId, 0)));

    // by initially setting a clientRequest, we communicate to the Queue Processor that it is the
    // coordinator
    var slot = new AgreementSlot(seqNum, clientRequest);

    when(msgSenderMock.getLowestPingReplicas(anyInt()))
        .thenReturn(new HashSet<>(List.of(ReplicaId.of(0), ReplicaId.of(3))));

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
            maxFaults,
            replicaCount);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    var msgCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    // Because the queueProcessor is in another thread, we use timeout to wait until the function is
    // called
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(false), msgCaptor.capture());

    // When the queueProcessor handles the clientRequest, it should broadcast the DepPropose and
    // client request
    var wrapper = msgCaptor.getValue();
    var depProposeWithRequest = (DepProposeWithRequest) wrapper.getPayload();
    var depPropose = depProposeWithRequest.depPropose();
    assertEquals(seqNum, depProposeWithRequest.seqNum());
    assertEquals(ownReplicaId.value(), wrapper.getSender());
    assertEquals(seqNum, depPropose.seqNum());
    assertEquals(ownReplicaId, depPropose.coordinatorId());
    assertEquals(clientRequestHash, depPropose.requestHash());
    assertEquals(new DependencySet(SequenceNumber.of(ownReplicaId, 0)), depPropose.depSet());
    assertEquals(Set.of(ReplicaId.of(3), ReplicaId.of(0)), depPropose.followerQuorum());

    // When other replicas receive the DepPropose message, they calculate their dependencies and
    // send a DepVerify message
    List<DepVerifyMessage> replies = new LinkedList<>();
    var depProposeHash = depPropose.calculateHash();

    // f+1 Replicas reply with an additional dependency, thus it has to be included in the DepCommit
    // The original dependency is included in all replies
    DependencySet finalDepSet =
        new DependencySet(SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(0, 0));

    // Only the replicas in the follower quorum send a DepVerify message
    for (var r : depPropose.followerQuorum()) {
      replies.add(new DepVerifyMessage(seqNum, r, depProposeHash, finalDepSet));
    }
    var depVerifysHash = DepVerifyMap.calculateDepVerifyHash(replies);

    // Send replies to our agreement slot
    incomingQueue.addAll(replies);

    // Now we expect a DepCommit message that is sent
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var depCommit = (DepCommitMessage) wrapper.getPayload();
    incomingQueue.add(depCommit);
    // as broadcast is called with includeSelf true, we have to add it to the incomingQueue

    assertEquals(seqNum, depCommit.seqNum());
    assertEquals(ownReplicaId, depCommit.replicaId());
    assertEquals(depVerifysHash, depCommit.depVerifysHash());

    // Replica has to receive 2f+1 matching DepCommits (including itself) -> send 2 DepCommits from
    // other followers
    DepCommitMessage depCommit1 = new DepCommitMessage(seqNum, otherReplicaIds[0], depVerifysHash);
    DepCommitMessage depCommit2 = new DepCommitMessage(seqNum, otherReplicaIds[1], depVerifysHash);

    incomingQueue.add(depCommit1);
    incomingQueue.add(depCommit2);

    var execCaptor = ArgumentCaptor.forClass(CommittedCommand.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execCaptor.capture());
    var execMessage = execCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(finalDepSet, execMessage.depSet());

    // After a request has been forwarded to execution, we are done!
  }

  /**
   * This tests the reconciliation path after the dependency set from the DepVerifys cannot be
   * confirmed.
   */
  @Test
  void testCoordinatorReconciliationPath() {
    var ownReplicaId = ReplicaId.of(2);
    var otherReplicaIds = new ReplicaId[] {ReplicaId.of(1), ReplicaId.of(0), ReplicaId.of(3)};
    var seqNum = SequenceNumber.of(ownReplicaId, 1);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();

    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any()))
        .thenReturn(
            new DependencySet(
                SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(otherReplicaIds[0], 0)));

    // by initially setting a clientRequest, we communicate to the Queue Processor that it is the
    // coordinator
    var slot = new AgreementSlot(seqNum, clientRequest);

    when(msgSenderMock.getLowestPingReplicas(anyInt()))
        .thenReturn(new HashSet<>(List.of(ReplicaId.of(0), ReplicaId.of(3))));

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
            maxFaults,
            replicaCount);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    var msgCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    // Because the queueProcessor is in another thread, we use timeout to wait until the function is
    // called
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(false), msgCaptor.capture());

    // When the queueProcessor handles the clientRequest, it should broadcast the DepPropose and
    // client request
    var wrapper = msgCaptor.getValue();
    var depProposeWithRequest = (DepProposeWithRequest) wrapper.getPayload();
    var depPropose = depProposeWithRequest.depPropose();
    assertEquals(seqNum, depProposeWithRequest.seqNum());
    assertEquals(ownReplicaId.value(), wrapper.getSender());
    assertEquals(seqNum, depPropose.seqNum());
    assertEquals(ownReplicaId, depPropose.coordinatorId());
    assertEquals(clientRequestHash, depPropose.requestHash());
    assertEquals(
        new DependencySet(
            SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(otherReplicaIds[0], 0)),
        depPropose.depSet());
    assertEquals(Set.of(ReplicaId.of(3), ReplicaId.of(0)), depPropose.followerQuorum());

    List<DepVerifyMessage> replies = new LinkedList<>();
    var depProposeHash = depPropose.calculateHash();

    DependencySet depSet1 =
        new DependencySet(
            SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(otherReplicaIds[1], 0));
    DependencySet depSet2 =
        new DependencySet(
            SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(otherReplicaIds[2], 0));

    replies.add(new DepVerifyMessage(seqNum, ReplicaId.of(0), depProposeHash, depSet1));
    replies.add(new DepVerifyMessage(seqNum, ReplicaId.of(3), depProposeHash, depSet2));
    incomingQueue.addAll(replies);

    var depVerifysHash = DepVerifyMap.calculateDepVerifyHash(replies);

    // As some dependencies do not have a f+1 quorum,
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var prepare = (PrepareMessage) wrapper.getPayload();
    incomingQueue.add(prepare);
    assertEquals(seqNum, prepare.seqNum());
    // view number only increases if a timeout triggers
    assertEquals(ViewNumber.of(-1), prepare.viewNumber());
    assertEquals(ownReplicaId, prepare.replicaId());
    assertEquals(depVerifysHash, prepare.depVerifysHash());

    // Page 5 ISOS: After a replica has obtained 2f+1 prepares matching the set of known DepVerifys,
    // the replica
    // has rp-prepared the agreement slot and continues with broadcasting a Commit message.

    // Create
    List<PrepareMessage> prepares = new LinkedList<>();
    prepares.add(
        new PrepareMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[0], depVerifysHash));
    prepares.add(
        new PrepareMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[1], depVerifysHash));
    incomingQueue.addAll(prepares);

    verify(msgSenderMock, timeout(500).times(2)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var commit = (CommitMessage) wrapper.getPayload();
    incomingQueue.add(commit);
    assertEquals(seqNum, commit.seqNum());
    assertEquals(ViewNumber.of(-1), commit.viewNumber());
    assertEquals(ownReplicaId, commit.replicaId());
    assertEquals(depVerifysHash, commit.depVerifysHash());

    List<CommitMessage> commits = new LinkedList<>();
    commits.add(new CommitMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[0], depVerifysHash));
    commits.add(new CommitMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[1], depVerifysHash));
    incomingQueue.addAll(commits);

    // After the coordinator receives 2f+1 commit messages (including its own), it can forward the
    // request to the execution

    // The final dependency set is the union of all dependency sets
    var depSetUnion =
        new DependencySet(
            SequenceNumber.of(ownReplicaId, 0),
            SequenceNumber.of(otherReplicaIds[0], 0),
            SequenceNumber.of(otherReplicaIds[1], 0),
            SequenceNumber.of(otherReplicaIds[2], 0));

    var execCaptor = ArgumentCaptor.forClass(CommittedCommand.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execCaptor.capture());
    var execMessage = execCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(depSetUnion, execMessage.depSet());
  }

  @Test
  void testFollowerHappyPath() {
    var coordinatorId = ReplicaId.of(0);

    var ownReplicaId = ReplicaId.of(1);
    var otherReplicaIds = new ReplicaId[] {ReplicaId.of(2), ReplicaId.of(0), ReplicaId.of(3)};

    var otherFollowerId = ReplicaId.of(3);
    var seqNum = SequenceNumber.of(coordinatorId, 1);

    var depSet = new DependencySet(SequenceNumber.of(0, 0));
    var followerQuorum = Set.of(ownReplicaId, otherFollowerId);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();
    var depPropose =
        new DepProposeMessage(seqNum, coordinatorId, clientRequestHash, depSet, followerQuorum);
    var depProposeHash = depPropose.calculateHash();
    var depProposeWithRequest = new DepProposeWithRequest(depPropose, clientRequest);

    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any()))
        .thenReturn(new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)));

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
            maxFaults,
            replicaCount);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    // Follower is in FollowerQuorum, so we have to broadcast a DepVerify
    incomingQueue.add(depProposeWithRequest);

    var msgCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    // DepVerify
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), msgCaptor.capture());

    var wrapper = msgCaptor.getValue();
    var depVerify = (DepVerifyMessage) wrapper.getPayload();
    incomingQueue.add(depVerify);
    assertEquals(seqNum, depVerify.seqNum());
    assertEquals(ownReplicaId, depVerify.followerId());
    assertEquals(depProposeHash, depVerify.depProposeHash());
    assertEquals(
        new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)), depVerify.depSet());

    // We still have to receive the second DepVerify from the other replica
    var otherDepVerify =
        new DepVerifyMessage(
            seqNum,
            otherFollowerId,
            depProposeHash,
            new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)));
    incomingQueue.add(otherDepVerify);

    var depVerifysHash = DepVerifyMap.calculateDepVerifyHash(List.of(otherDepVerify, depVerify));
    var depVerifysHashSwapped =
        DepVerifyMap.calculateDepVerifyHash(List.of(depVerify, otherDepVerify));
    assertEquals(depVerifysHash, depVerifysHashSwapped);

    // With the DepPropose, own DepVerify, and DepVerify from other replica with matching
    // Dependencies, the follower broadcasts a DepCommit message
    verify(msgSenderMock, timeout(500).times(2)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var depCommit = (DepCommitMessage) wrapper.getPayload();
    incomingQueue.add(depCommit);
    assertEquals(seqNum, depCommit.seqNum());
    assertEquals(ownReplicaId, depCommit.replicaId());
    assertEquals(depVerifysHash, depCommit.depVerifysHash());

    // Create the remaining 2f depCommit messages
    var depCommit1 = new DepCommitMessage(seqNum, coordinatorId, depVerifysHash);
    var depCommit2 = new DepCommitMessage(seqNum, otherFollowerId, depVerifysHash);
    incomingQueue.add(depCommit1);
    incomingQueue.add(depCommit2);

    var execCaptor = ArgumentCaptor.forClass(CommittedCommand.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execCaptor.capture());
    var execMessage = execCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(
        new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)),
        execMessage.depSet());
  }

  @Test
  void testFollowerReconciliationPath() {
    var coordinatorId = ReplicaId.of(1);
    var ownReplicaId = ReplicaId.of(3);
    var otherFollowerId = ReplicaId.of(0);
    var otherReplicaIds = new ReplicaId[] {ReplicaId.of(0), ReplicaId.of(1), ReplicaId.of(2)};

    var seqNum = SequenceNumber.of(coordinatorId, 3);
    var depSet = new DependencySet(SequenceNumber.of(0, 0));
    var followerQuorum = Set.of(ownReplicaId, otherFollowerId);

    var clientRequest = new OrderedClientRequest(1, "MyCommand".getBytes(), 0L);
    var clientRequestHash = clientRequest.calculateHash();
    var depPropose =
        new DepProposeMessage(seqNum, coordinatorId, clientRequestHash, depSet, followerQuorum);
    var depProposeHash = depPropose.calculateHash();
    var depProposeWithRequest = new DepProposeWithRequest(depPropose, clientRequest);

    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any()))
        .thenReturn(new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)));

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
            maxFaults,
            replicaCount);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    // Follower is in FollowerQuorum, so we have to broadcast dePVerify
    incomingQueue.add(depProposeWithRequest);

    var msgCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);

    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), msgCaptor.capture());

    var wrapper = msgCaptor.getValue();
    var depVerify = (DepVerifyMessage) wrapper.getPayload();
    incomingQueue.add(depVerify);
    assertEquals(seqNum, depVerify.seqNum());
    assertEquals(ownReplicaId, depVerify.followerId());
    assertEquals(depProposeHash, depVerify.depProposeHash());
    assertEquals(
        new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0)), depVerify.depSet());

    var otherDepVerify =
        new DepVerifyMessage(
            seqNum, otherFollowerId, depProposeHash, new DependencySet(SequenceNumber.of(0, 0)));
    incomingQueue.add(otherDepVerify);

    // Technically there are f+1 replicas with the SequenceNumber(1,0) in the DependencySet, but as
    // the f+1 does not come from the follower Quroum, the ISOS paper tells us that we have to go to
    // the reconciliation path

    var depVerifysHash = DepVerifyMap.calculateDepVerifyHash(List.of(otherDepVerify, depVerify));

    verify(msgSenderMock, timeout(500).times(2)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var prepare = (PrepareMessage) wrapper.getPayload();
    incomingQueue.add(prepare);
    assertEquals(seqNum, prepare.seqNum());
    assertEquals(ViewNumber.of(-1), prepare.viewNumber());
    assertEquals(ownReplicaId, prepare.replicaId());
    assertEquals(depVerifysHash, prepare.depVerifysHash());

    // Create the remaining prepare messages
    List<PrepareMessage> prepares = new LinkedList<>();
    prepares.add(
        new PrepareMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[0], depVerifysHash));
    prepares.add(
        new PrepareMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[1], depVerifysHash));
    incomingQueue.addAll(prepares);

    verify(msgSenderMock, timeout(500).times(3)).broadcastToReplicas(eq(true), msgCaptor.capture());

    wrapper = msgCaptor.getValue();
    var commit = (CommitMessage) wrapper.getPayload();
    incomingQueue.add(commit);
    assertEquals(seqNum, commit.seqNum());
    assertEquals(ViewNumber.of(-1), commit.viewNumber());
    assertEquals(ownReplicaId, commit.replicaId());
    assertEquals(depVerifysHash, commit.depVerifysHash());

    List<CommitMessage> commits = new LinkedList<>();
    commits.add(new CommitMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[0], depVerifysHash));
    commits.add(new CommitMessage(seqNum, ViewNumber.of(-1), otherReplicaIds[1], depVerifysHash));
    incomingQueue.addAll(commits);

    var depSetUnion = new DependencySet(SequenceNumber.of(0, 0), SequenceNumber.of(1, 0));

    var execCaptor = ArgumentCaptor.forClass(CommittedCommand.class);
    verify(requestExecutorMock, timeout(500)).forwardRequestToExecution(execCaptor.capture());
    var execMessage = execCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(depSetUnion, execMessage.depSet());
  }

  @Test
  void testOutOfOrderMessages() throws Exception {
    var ownReplicaId = ReplicaId.of(2);
    var otherReplicaIds = new ReplicaId[] {ReplicaId.of(1), ReplicaId.of(0), ReplicaId.of(3)};
    var seqNum = SequenceNumber.of(ownReplicaId, 2);
    var clientRequest = new OrderedClientRequest(2, "OutOfOrder".getBytes(), 0L);

    ConflictChecker conflictChecker = mock(ConflictChecker.class);
    when(conflictChecker.getCompactDependencySet(any(), any()))
        .thenReturn(new DependencySet(SequenceNumber.of(ownReplicaId, 0)));

    var slot = new AgreementSlot(seqNum, clientRequest);
    when(msgSenderMock.getLowestPingReplicas(anyInt()))
        .thenReturn(new HashSet<>(List.of(ReplicaId.of(0), ReplicaId.of(3))));

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
            maxFaults,
            replicaCount);
    var queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.start();

    var msgCaptor = ArgumentCaptor.forClass(ISOSMessageWrapper.class);
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(false), msgCaptor.capture());
    var wrapper = msgCaptor.getValue();
    var depProposeWithRequest = (DepProposeWithRequest) wrapper.getPayload();
    var depPropose = depProposeWithRequest.depPropose();
    var depProposeHash = depPropose.calculateHash();
    DependencySet finalDepSet =
        new DependencySet(SequenceNumber.of(ownReplicaId, 0), SequenceNumber.of(0, 0));

    // Prepare DepVerify replies (follower quorum)
    List<DepVerifyMessage> depVerifyReplies = new LinkedList<>();
    for (var r : depPropose.followerQuorum()) {
      depVerifyReplies.add(new DepVerifyMessage(seqNum, r, depProposeHash, finalDepSet));
    }
    var depVerifysHash = DepVerifyMap.calculateDepVerifyHash(depVerifyReplies);

    // Prepare DepCommit messages
    DepCommitMessage depCommit1 = new DepCommitMessage(seqNum, otherReplicaIds[0], depVerifysHash);
    DepCommitMessage depCommit2 = new DepCommitMessage(seqNum, otherReplicaIds[1], depVerifysHash);

    // Send DepCommit messages before DepVerify messages out of order
    incomingQueue.add(depCommit1);
    incomingQueue.add(depCommit2);

    // Now send DepVerify messages
    incomingQueue.addAll(depVerifyReplies);

    // After receiving 2f DepVerify messages, the coordinator broadcasts a DepCommit message.
    // Receive the DepCommit message from the outgoing queue and loopback.
    verify(msgSenderMock, timeout(500)).broadcastToReplicas(eq(true), msgCaptor.capture());
    wrapper = msgCaptor.getValue();
    var depCommit = (DepCommitMessage) wrapper.getPayload();
    incomingQueue.add(depCommit);

    // Wait for execution to be triggered
    var execCaptor = ArgumentCaptor.forClass(CommittedCommand.class);
    verify(requestExecutorMock, timeout(1000)).forwardRequestToExecution(execCaptor.capture());
    var execMessage = execCaptor.getValue();
    assertEquals(seqNum, execMessage.seqNum());
    assertEquals(clientRequest, execMessage.clientRequest());
    assertEquals(finalDepSet, execMessage.depSet());
  }

  /** A replica that is not included in the followerQuorum of the DepPropose message. */
  @Test
  void testNotFollowerReplicaFastPath() {
    // FIXME
  }
}
