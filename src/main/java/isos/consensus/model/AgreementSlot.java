package isos.consensus.model;

import isos.consensus.ViewNumberNotLargerException;
import isos.consensus.model.viewchange.EmptyCertificate;
import isos.consensus.model.viewchange.ViewChangeCertificate;
import isos.execution.CommittedCommand;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Single agreement slot. Used solely as a data class. Logic is contained in the
 * AgreementSlotSequence and the AgreementSlotManager. All maps are returned as unmodifiable, and
 * values have to be put via the respective set method for a single entry.
 */
public class AgreementSlot {
  // s_j: Agreement Slot s_j
  private SequenceNumber seqNum;
  // Contains client ID, client-local timestamp, and command
  private OrderedClientRequest request;
  // p: DepPropose for slot s_j includes fast path quorum F
  private DepProposeMessage depPropose;

  /**
   * v[f_i]: DepVerify for slot s_j from follower f_i This map only contains DepVerify messages
   * received from followers that are defined in the follower quorum F of the depPropose message.
   */
  private final DepVerifyMap depVerifys;

  // current phase
  private AgreementSlotPhase step;
  private final ViewChangeMap viewChanges;
  // View number for slot s_j, initially -1
  private ViewNumber viewNumber;
  // Highest view number for slot s_j seen for replica r_i
  private final Map<ReplicaId, ViewNumber> peerViewNumbers;

  // DECIDED Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in
  // the AgreementSlotSequence? -> only stored in the sequence, so that coordination does not have
  // to be
  // DECIDED kai: Should AgreementSlot be record, or normal object? -> normal object, due to
  // frequent changes to the fields

  private ViewChangeCertificate viewChangeCertificate;

  private CommittedCommand exec;

  // Fields required for waiting / notifying efficiently (not for concurrency control)

  private final Lock slotLock;

  /**
   * When the DepPropose, DepVerify, or ViewChange messages change, this condition has to be
   * notified
   */
  private final Condition messageCountCondition;

  public AgreementSlot(SequenceNumber seqNum) {
    this(seqNum, null);
  }

  /**
   * @param seqNum The sequence number of this agreement slot.
   * @param r When the ClientRequest is not null, the {@link isos.consensus.AgmtSlotQueueProcessor}
   *     acts as the coordinator for this agreement slot when this AgreementSlot object is passed to
   *     it. When it is null, it acts as the follower.
   */
  public AgreementSlot(SequenceNumber seqNum, OrderedClientRequest r) {
    this(
        seqNum,
        r,
        null,
        AgreementSlotPhase.INIT,
        new HashMap<>(),
        ViewNumber.defaultViewNumber(),
        new HashMap<>(),
        new EmptyCertificate());
  }

  public AgreementSlot(
      SequenceNumber seqNum,
      OrderedClientRequest request,
      DepProposeMessage depPropose,
      AgreementSlotPhase step,
      Map<ReplicaId, ViewChangeMessage> viewChanges,
      ViewNumber viewNumber,
      Map<ReplicaId, ViewNumber> peerViewNumbers,
      ViewChangeCertificate viewChangeCertificate) {
    this.seqNum = seqNum;
    this.request = request;
    this.depPropose = depPropose;
    this.depVerifys = new DepVerifyMap();
    this.step = step;
    this.viewChanges = new ViewChangeMap();
    this.viewNumber = viewNumber;
    this.peerViewNumbers = peerViewNumbers;
    this.viewChangeCertificate = viewChangeCertificate;

    this.slotLock = new ReentrantLock();
    this.messageCountCondition = this.slotLock.newCondition();
  }

  public void awaitConditionCompleted(int quorumSize) throws InterruptedException {
    this.slotLock.lock();
    try {
      while (this.depPropose == null // received valid DepPropose
          && !this.reachedDepVerifyQuorum(quorumSize) // received f+1 correctly signed DepVerifys
          && !this.reachedViewChangeQuorum(
              this.viewNumber, quorumSize) // received f+1 correctly signed ViewChanges (of the view that we are currently in)
      // TODO Kai: do we have to check for a correct view number here?
      ) {
        // if depPropose, depVerify, or viewChanges get changed, the condition will be notified
        this.messageCountCondition.await();
      }
    } finally {
      this.slotLock.unlock();
    }
  }

  public SequenceNumber getSeqNum() {
    return seqNum;
  }

  public void setSeqNum(SequenceNumber seqNum) {
    this.seqNum = seqNum;
  }

  public OrderedClientRequest getRequest() {
    return request;
  }

  public void setRequest(OrderedClientRequest request) {
    this.request = request;
  }

  public DepProposeMessage getDepPropose() {
    return depPropose;
  }

  public void setDepPropose(DepProposeMessage depPropose) {
    if (this.depPropose != null) {
      throw new IllegalStateException("DepPropose cannot be set again");
    }

    this.slotLock.lock();
    try {
      this.depPropose = depPropose;
      this.depVerifys.setFollowerQuroum(depPropose.followerQuorum());
    } finally {
      // We are signaling so that other agreement slots that wait for us can check the wait
      // condition
      this.messageCountCondition.signalAll();
      this.slotLock.unlock();
    }
  }

  public Map<ReplicaId, DepVerifyMessage> getDepVerifys() {
    return depVerifys.getDepVerifys();
  }

  public void setDepVerify(ReplicaId replicaId, DepVerifyMessage depVerify) {
    this.slotLock.lock();
    try {
      this.depVerifys.setDepVerify(replicaId, depVerify);
    } finally {
      this.messageCountCondition.signalAll();
      this.slotLock.unlock();
    }
  }

  /**
   * Used in Pseudocode line 129/130 in case of a view change. All previous DepVerifys should be
   * deleted.
   */
  public void replaceDepVerifys(List<DepVerifyMessage> depVerifys) {
    this.slotLock.lock();
    try {
      this.depVerifys.clearDepVerifys();
      for (var d: depVerifys) {
        this.depVerifys.setDepVerify(d.followerId(), d);
      }
    } finally {
      this.messageCountCondition.signalAll();
      this.slotLock.unlock();
    }
  }

  public boolean isFpVerified(int maxFaults) {
    return this.depVerifys.isFpVerified(maxFaults);
  }

  /**
   * Not thread-safe.
   *
   * @return
   */
  public String getDepVerifyHashCached() {
    return this.depVerifys.getdepVerifyHashCached();
  }

  public boolean reachedDepVerifyQuorum(int quorumSize) {
    return this.depVerifys.reachedQuorum(quorumSize);
  }

  public boolean reachedViewChangeQuorum(ViewNumber viewNumber, int quorumSize) {
    return this.viewChanges.reachedQuorum(viewNumber, quorumSize);
  }

  public AgreementSlotPhase getStep() {
    return step;
  }

  public void setStep(AgreementSlotPhase step) {
    this.step = step;
  }

  public Map<ReplicaId, ViewChangeMessage> getViewChanges(ViewNumber viewNumber) {
    return this.viewChanges.getViewChanges(viewNumber);
  }

  public void setViewChange(ViewChangeMessage viewChange) {
    this.slotLock.lock();
    try {
      this.viewChanges.setViewChange(viewChange);
    } finally {
      this.messageCountCondition.signalAll();
      this.slotLock.unlock();
    }
  }

  /**
   * Returns the current view number of the agreement slot.
   * @return
   */
  public ViewNumber getViewNumber() {
    return viewNumber;
  }

  public void setViewNumber(ViewNumber viewNumber) {
    this.viewNumber = viewNumber;
  }

  public Map<ReplicaId, ViewNumber> getPeerViewNumbers() {
    return Collections.unmodifiableMap(peerViewNumbers);
  }

  /**
   * Returns the current view number of the given replica id. If no ViewNumber has been set before,
   *
   * @param replicaId
   * @return
   */
  public ViewNumber getPeerViewNumber(ReplicaId replicaId) {
    return this.peerViewNumbers.getOrDefault(replicaId, ViewNumber.of(-1));
  }

  public void setPeerViewNumber(ReplicaId replicaId, ViewNumber newPeerViewNumber) {
    var currentViewNumber = this.peerViewNumbers.get(replicaId);
    if (currentViewNumber != null && newPeerViewNumber.compareTo(currentViewNumber) <= 0) { // smaller or equal
      throw new ViewNumberNotLargerException(currentViewNumber, newPeerViewNumber);
    }

    this.peerViewNumbers.put(replicaId, newPeerViewNumber);
  }

  public Optional<ViewNumber> getHighestViewNumberByQuorum(int quorumSize) {
    return AgreementSlot.getHighestViewNumberByQuorum(this.peerViewNumbers, quorumSize);
  }

  public static Optional<ViewNumber> getHighestViewNumberByQuorum(
      Map<ReplicaId, ViewNumber> viewNumbers, int quorumSize) {
    Map<ViewNumber, Long> groupedByCount =
        viewNumbers.values().stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));

    var result = groupedByCount.entrySet().stream()
        .filter(entry -> entry.getValue() >= quorumSize)
        .max(Map.Entry.comparingByKey());

    return result.map(Map.Entry::getKey);
  }

  public ViewChangeCertificate getViewChangeCertificate() {
    return viewChangeCertificate;
  }

  public void setViewChangeCertificate(ViewChangeCertificate viewChangeCertificate) {
    this.viewChangeCertificate = viewChangeCertificate;
  }

  public CommittedCommand getExec() {
    return exec;
  }

  public void setExec(CommittedCommand exec) {
    this.exec = exec;
  }
}
