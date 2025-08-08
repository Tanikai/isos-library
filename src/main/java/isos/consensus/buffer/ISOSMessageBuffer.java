package isos.consensus.buffer;

import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.fast.DepCommitMessage;
import isos.message.replica.reconciliation.CommitMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A data structure to store messages that have to be buffered and handled later. Use cases:
 *
 * <ul>
 *   <li>Deferred messages due to a step mismatch.
 *   <li>Messages that can only be processed after a quorum is reached.
 * </ul>
 */
public class ISOSMessageBuffer {

  private final Map<ISOSMessageType, Map<ReplicaId, ISOSMessage>> bufferedMessages;

  /** Stores processed messages until a Quorum size is reached. */
  /** The messages for the DepCommit quorum can be stored as a simple map. */
  private final Map<ReplicaId, DepCommitMessage> depCommitQuorum;

  /** Messages for prepare and commit have to be separated by their view number. */
  private final Map<ViewNumber, Map<ReplicaId, PrepareMessage>> prepareQuorum;

  private final Map<ViewNumber, Map<ReplicaId, CommitMessage>> commitQuorum;

  private final Map<ViewNumber, Map<ReplicaId, ViewChangeMessage>> viewChangeQuorum;

  public ISOSMessageBuffer() {
    this.bufferedMessages = new HashMap<>();

    // Creates hashmap for message types that do not have to be buffered as well
    for (var type : ISOSMessageType.values()) {
      this.bufferedMessages.put(type, new HashMap<>());
    }

    this.depCommitQuorum = new HashMap<>();
    this.prepareQuorum = new HashMap<>();
    this.commitQuorum = new HashMap<>();
    this.viewChangeQuorum = new HashMap<>();
  }

  public void bufferMessage(ISOSMessage msg) throws ReplicaMessageAlreadyPresent {
    var msgType = msg.msgType();
    var msgMap = this.bufferedMessages.get(msgType);
    var sender = msg.logicalSender();

    if (msgMap.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, msgType);
    }

    msgMap.put(sender, msg);
  }

  @SuppressWarnings(value = "unchecked")
  public <T> Collection<T> getAllMessages(ISOSMessageType msgType) {
    // TODO Kai: should the buffered messages be removed completely or not?
    // TODO Kai: write unit tests with out of order messages
    return (Collection<T>) this.bufferedMessages.get(msgType).values();
  }

  public void storeDepCommit(DepCommitMessage depCommit) throws ReplicaMessageAlreadyPresent {
    var sender = depCommit.replicaId();
    if (this.depCommitQuorum.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, depCommit.msgType());
    }

    this.depCommitQuorum.put(sender, depCommit);
  }

  public boolean depCommitQuorumReached(int quorumSize) {
    return depCommitQuorum.size() >= quorumSize;
  }

  public boolean depCommitQuorumWithSameHashReached(String depVerifiesHash, int quorumSize) {
    if (!depCommitQuorumReached(quorumSize)) {
      return false;
    }

    long sameHashCount =
        this.depCommitQuorum.values().stream()
            .filter(msg -> depVerifiesHash.equals(msg.depVerifiesHash()))
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

  public boolean prepareQuorumReached(ViewNumber viewNumber, int quorumSize) {
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

  public boolean commitQuorumReached(ViewNumber viewNumber, int quorumSize) {
    return this.commitQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).size() >= quorumSize;
  }

  public Collection<CommitMessage> getCommits(ViewNumber viewNumber) {
    return this.commitQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).values();
  }

  public void storeViewChange(ViewChangeMessage viewChange) throws ReplicaMessageAlreadyPresent {
    var sender = viewChange.replicaId();
    var viewNumber = viewChange.viewNumber();

    var viewChanges = this.viewChangeQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>());

    if (viewChanges.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, viewNumber, viewChange.msgType());
    }

    viewChanges.put(sender, viewChange);
  }

  public boolean viewChangeQuorumReached(ViewNumber viewNumber, int quorumSize) {
    return this.viewChangeQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).size()
        >= quorumSize;
  }

  public Collection<ViewChangeMessage> getViewChanges(ViewNumber viewNumber) {
    return this.viewChangeQuorum.computeIfAbsent(viewNumber, x -> new HashMap<>()).values();
  }
}
