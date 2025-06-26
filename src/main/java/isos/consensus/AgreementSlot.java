package isos.consensus;

import isos.message.OrderedClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.message.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * Single agreement slot. Used solely as a data class. Logic is contained in the
 * AgreementSlotSequence and the AgreementSlotManager.
 */
public class AgreementSlot {
  // s_j: Agreement Slot s_j
  private SequenceNumber seqNum;
  // Contains client ID, client-local timestamp, and command
  private OrderedClientRequest request;
  // p: DepPropose for slot s_j includes fast path quorum F
  private DepProposeMessage depPropose;
  // v[f_i]: DepVerify  for slot s_j from follower f_i
  private Map<ReplicaId, DepVerifyMessage> depVerifies;
  // current phase
  private AgreementSlotPhase step;
  private Map<ReplicaId, ViewChangeMessage> viewChanges;
  // View number for slot s_j, initially -1
  private ViewNumber viewNumber;
  // Highest view number for slot s_j seen for replica r_i
  private Map<ReplicaId, ViewNumber> peerViewNumbers;

  // DECISION Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in
  // the AgreementSlotSequence? -> only stored in the sequence, so that coordination does not have to be
  // DECISION kai: Should AgreementSlot be record, or normal object? - >

  public AgreementSlot(SequenceNumber seqNum) {
    this(seqNum, null);
  }

  public AgreementSlot(SequenceNumber seqNum, OrderedClientRequest r) {
    this(seqNum, r, null, new HashMap<>(), AgreementSlotPhase.NULL, new HashMap<>(), new ViewNumber(), new HashMap<>());
  }

  public AgreementSlot(SequenceNumber seqNum, OrderedClientRequest request, DepProposeMessage depPropose,
                       Map<ReplicaId, DepVerifyMessage> depVerifies, AgreementSlotPhase step,
                       Map<ReplicaId, ViewChangeMessage> viewChanges, ViewNumber viewNumber,
                       Map<ReplicaId, ViewNumber> peerViewNumbers) {
    this.seqNum = seqNum;
    this.request = request;
    this.depPropose = depPropose;
    this.depVerifies = depVerifies;
    this.step = step;
    this.viewChanges = viewChanges;
    this.viewNumber = viewNumber;
    this.peerViewNumbers = peerViewNumbers;
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

  public Map<ReplicaId, DepVerifyMessage> getDepVerifies() {
    return depVerifies;
  }

  public void setDepVerifies(Map<ReplicaId, DepVerifyMessage> depVerifies) {
    this.depVerifies = depVerifies;
  }

  public AgreementSlotPhase getStep() {
    return step;
  }

  public void setStep(AgreementSlotPhase step) {
    this.step = step;
  }

  public Map<ReplicaId, ViewChangeMessage> getViewChanges() {
    return viewChanges;
  }

  public void setViewChanges(Map<ReplicaId, ViewChangeMessage> viewChanges) {
    this.viewChanges = viewChanges;
  }

  public ViewNumber getViewNumber() {
    return viewNumber;
  }

  public void setViewNumber(ViewNumber viewNumber) {
    this.viewNumber = viewNumber;
  }

  public Map<ReplicaId, ViewNumber> getPeerViewNumbers() {
    return peerViewNumbers;
  }

  public void setPeerViewNumbers(Map<ReplicaId, ViewNumber> peerViewNumbers) {
    this.peerViewNumbers = peerViewNumbers;
  }
}
