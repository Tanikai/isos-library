package isos.consensus.model;

import isos.consensus.model.viewchange.EmptyCertificate;
import isos.consensus.model.viewchange.ViewChangeCertificate;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
  private DepVerifyMap depVerifies;

  // current phase
  private AgreementSlotPhase step;
  private Map<ReplicaId, ViewChangeMessage> viewChanges;
  // View number for slot s_j, initially -1
  private ViewNumber viewNumber;
  // Highest view number for slot s_j seen for replica r_i
  private Map<ReplicaId, ViewNumber> peerViewNumbers;

  // DECISION Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in
  // the AgreementSlotSequence? -> only stored in the sequence, so that coordination does not have
  // to be
  // DECISION kai: Should AgreementSlot be record, or normal object? -> normal object, due to
  // frequent changes to the fields

  private ViewChangeCertificate viewChangeCertificate;

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
        new HashMap<>(),
        AgreementSlotPhase.INIT,
        new HashMap<>(),
        new ViewNumber(),
        new HashMap<>(),
        new EmptyCertificate());
  }

  public AgreementSlot(
      SequenceNumber seqNum,
      OrderedClientRequest request,
      DepProposeMessage depPropose,
      Map<ReplicaId, DepVerifyMessage> depVerifies,
      AgreementSlotPhase step,
      Map<ReplicaId, ViewChangeMessage> viewChanges,
      ViewNumber viewNumber,
      Map<ReplicaId, ViewNumber> peerViewNumbers,
      ViewChangeCertificate viewChangeCertificate) {
    this.seqNum = seqNum;
    this.request = request;
    this.depPropose = depPropose;
    this.depVerifies = new DepVerifyMap();
    this.step = step;
    this.viewChanges = viewChanges;
    this.viewNumber = viewNumber;
    this.peerViewNumbers = peerViewNumbers;
    this.viewChangeCertificate = viewChangeCertificate;
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
    this.depPropose = depPropose;
  }

  // TODO Kai: Instead of returning the whole map as mutable, we could return an immutable map and
  // instead set via a key-specific setter method?
  public Map<ReplicaId, DepVerifyMessage> getDepVerifies() {
    return depVerifies.getDepVerifies();
  }

  public void setDepVerify(ReplicaId replicaId, DepVerifyMessage depVerify) {
    this.depVerifies.setDepVerify(replicaId, depVerify);
  }

  /**
   * Not thread-safe.
   * @return
   */
  public String getDepVerifyHashCached() {
    return this.depVerifies.getdepVerifyHashCached();
  }

  public AgreementSlotPhase getStep() {
    return step;
  }

  public void setStep(AgreementSlotPhase step) {
    this.step = step;
  }

  public Map<ReplicaId, ViewChangeMessage> getViewChanges() {
    return Collections.unmodifiableMap(viewChanges);
  }

  public void setViewChange(ReplicaId replicaId, ViewChangeMessage viewChange) {
    this.viewChanges.put(replicaId, viewChange);
  }

  public ViewNumber getViewNumber() {
    return viewNumber;
  }

  public void setViewNumber(ViewNumber viewNumber) {
    this.viewNumber = viewNumber;
  }

  public Map<ReplicaId, ViewNumber> getPeerViewNumbers() {
    return Collections.unmodifiableMap(peerViewNumbers);
  }

  public void setPeerViewNumber(ReplicaId replicaId, ViewNumber peerViewNumber) {
    this.peerViewNumbers.put(replicaId, peerViewNumber);
  }

  public ViewChangeCertificate getViewChangeCertificate() {
    return viewChangeCertificate;
  }

  public void setViewChangeCertificate(ViewChangeCertificate viewChangeCertificate) {
    this.viewChangeCertificate = viewChangeCertificate;
  }
}
