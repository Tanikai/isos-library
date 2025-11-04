package isos.consensus.buffer;

import isos.consensus.model.DependencySet;
import isos.message.replica.ClientRequestBatch;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.ISOSMessageWithViewNumber;
import isos.message.replica.fast.DepCommitMessage;
import isos.message.replica.reconciliation.CommitMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.message.replica.viewchange.ExecMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.*;

/**
 * A data structure to store messages that have to be buffered for processing later, or to store to
 * check whether a quorum has been reached or not.
 *
 * <ul>
 *   <li>Deferred messages due to a step mismatch.
 *   <li>Messages that can only be processed after a quorum is reached.
 * </ul>
 */
public class ISOSMessageBuffer {

  private final Map<ISOSMessageType, Map<ReplicaId, ISOSMessage>> bufferedMessages;
  private final Map<ViewNumber, Map<ISOSMessageType, Map<ReplicaId, ISOSMessageWithViewNumber>>>
      viewBufferedMessages;

  /** Stores processed messages until a Quorum size is reached. */
  /** The messages for the DepCommit quorum can be stored as a simple map. */
  private final Map<ReplicaId, DepCommitMessage> depCommitQuorum;

  /** Messages for prepare and commit have to be separated by their view number. */
  private final Map<ViewNumber, Map<ReplicaId, PrepareMessage>> prepareQuorum;

  private final Map<ViewNumber, Map<ReplicaId, CommitMessage>> commitQuorum;

  private final Map<ReplicaId, ExecMessage> execQuorum;

  public ISOSMessageBuffer() {
    this.bufferedMessages = new HashMap<>();
    this.viewBufferedMessages = new HashMap<>();

    // Creates hashmap for message types that do not have to be buffered as well
    for (var type : ISOSMessageType.values()) {
      this.bufferedMessages.put(type, new HashMap<>());
    }

    this.depCommitQuorum = new HashMap<>();
    this.prepareQuorum = new HashMap<>();
    this.commitQuorum = new HashMap<>();
    this.execQuorum = new HashMap<>();
  }

  public void bufferMessage(ISOSMessage msg) throws ReplicaMessageAlreadyPresent {
    var msgType = msg.msgType();
    var sender = msg.logicalSender();

    if (msg instanceof ISOSMessageWithViewNumber withViewNumber) {
      // When the message has a ViewNumber (Reconciliation Path / View Change), we have store it in
      // another structure
      var viewNumber = withViewNumber.viewNumber();
      var buffer =
          this.viewBufferedMessages.computeIfAbsent(
              viewNumber,
              x -> {
                HashMap<ISOSMessageType, Map<ReplicaId, ISOSMessageWithViewNumber>> result =
                    new HashMap<>();
                for (var type : ISOSMessageType.values()) {
                  result.put(type, new HashMap<>());
                }
                return result;
              });
      var msgMap = buffer.get(msgType);
      if (msgMap.containsKey(sender)) {
        throw new ReplicaMessageAlreadyPresent(sender, viewNumber, msgType);
      }
      msgMap.put(sender, withViewNumber);
    } else {
      // Message does not have a ViewNumber (Fast Path)
      var msgMap = this.bufferedMessages.get(msgType);
      if (msgMap.containsKey(sender)) {
        throw new ReplicaMessageAlreadyPresent(sender, msgType);
      }
      msgMap.put(sender, msg);
    }
  }

  /**
   * Returns buffered messages that do not contain a view number. For messages with a view number,
   * see {@link #removeBufferedMsgWithView(ISOSMessageType, ViewNumber)}
   *
   * @param msgType
   * @return
   */
  public Collection<ISOSMessage> removeBufferedMsgWithoutView(ISOSMessageType msgType) {
    Map<ReplicaId, ISOSMessage> msgTypeMap = this.bufferedMessages.get(msgType);
    if (msgTypeMap.isEmpty()) {
      return List.of();
    }

    var msgs = new ArrayList<>(msgTypeMap.values());
    msgTypeMap.clear();
    return msgs;
  }

  /**
   * Returns buffered messages that have a view number. For messages without a view number, see
   * {@link #removeBufferedMsgWithoutView(ISOSMessageType)}.
   *
   * @param msgType
   * @param currentView
   * @return
   */
  public Collection<ISOSMessageWithViewNumber> removeBufferedMsgWithView(
      ISOSMessageType msgType, ViewNumber currentView) {
    if (!this.viewBufferedMessages.containsKey(currentView)) {
      return Set.of();
    }
    Map<ReplicaId, ISOSMessageWithViewNumber> msgTypeMap =
        this.viewBufferedMessages.get(currentView).get(msgType);
    if (msgTypeMap.isEmpty()) {
      return List.of();
    }
    var msgs = new ArrayList<>(msgTypeMap.values());
    msgTypeMap.clear();
    return msgs;
  }

  public void storeDepCommit(DepCommitMessage depCommit) throws ReplicaMessageAlreadyPresent {
    var sender = depCommit.replicaId();
    if (this.depCommitQuorum.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, depCommit.msgType());
    }

    this.depCommitQuorum.put(sender, depCommit);
  }

  /**
   * This should not be used to check whether a DepCommit quorum has been reached.
   *
   * @param quorumSize
   * @return
   */
  public boolean depCommitQuorumSizeReached(int quorumSize) {
    return depCommitQuorum.size() >= quorumSize;
  }

  public boolean depCommitQuorumWithSameHashReached(String depVerifysHash, int quorumSize) {
    if (!depCommitQuorumSizeReached(quorumSize)) {
      return false;
    }

    long sameHashCount =
        this.depCommitQuorum.values().stream()
            .filter(msg -> depVerifysHash.equals(msg.depVerifysHash()))
            .count();

    return sameHashCount >= quorumSize;
  }

  public Collection<DepCommitMessage> getDepCommits() {
    return this.depCommitQuorum.values();
  }

  public void storePrepare(PrepareMessage prepare) throws ReplicaMessageAlreadyPresent {
    var sender = prepare.replicaId();
    var viewNumber = prepare.viewNumber();

    var prepares = this.prepareQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>());

    if (prepares.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, viewNumber, prepare.msgType());
    }

    prepares.put(sender, prepare);
  }

  public boolean prepareQuorumSizeReached(ViewNumber viewNumber, int quorumSize) {
    return this.prepareQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).size()
        >= quorumSize;
  }

  public Collection<PrepareMessage> getPrepares(ViewNumber viewNumber) {
    return this.prepareQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).values();
  }

  public void storeCommit(CommitMessage commit) throws ReplicaMessageAlreadyPresent {
    var sender = commit.replicaId();
    var viewNumber = commit.viewNumber();

    var commits = this.commitQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>());

    if (commits.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, viewNumber, commit.msgType());
    }

    commits.put(sender, commit);
  }

  public boolean commitQuorumSizeReached(ViewNumber viewNumber, int quorumSize) {
    return this.commitQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).size() >= quorumSize;
  }

  public Collection<CommitMessage> getCommits(ViewNumber viewNumber) {
    return this.commitQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).values();
  }

  public void storeExec(ExecMessage exec) throws ReplicaMessageAlreadyPresent {
    var sender = exec.replicaId();
    if (this.execQuorum.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, exec.msgType());
    }

    this.execQuorum.put(sender, exec);
  }

  public boolean execQuorumSizeReached(int quorumSize) {
    return execQuorum.size() >= quorumSize;
  }

  public boolean execQuorumWithSameContentsReached(
          ClientRequestBatch clientRequest, DependencySet depSet, int quorumSize) {
    if (!execQuorumSizeReached(quorumSize)) {
      return false;
    }

    // Client request and Dependency Set has to be the same
    long sameCount =
        this.execQuorum.values().stream()
            .filter(
                msg ->
                    clientRequest.equals(msg.clientRequests()) && depSet.equals(msg.dependencySet()))
            .count();

    return sameCount >= quorumSize;
  }
}
