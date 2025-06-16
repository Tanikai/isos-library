package isos.message.viewchange;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import javax.sound.midi.Sequence;
import java.util.List;
import java.util.Set;

public class ViewChangeMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ViewNumber viewNumber; // v_{s_i}: New view number
  private ReplicaId coordinatorId; // co: Newly selected coordinator of view
  private DepProposeMessage depPropose; // dp: Original DepPropose message
  private List<DepVerifyMessage> depVerifies; // \vec{dv}: Accompanying DepVerifys
  private Set<NewViewMessage> viewChanges; // VCS: Set of 2f+1 ViewChanges

  public ViewChangeMessage(
      ReplicaId senderID,
      SequenceNumber seqNum,
      ViewNumber viewNumber,
      ReplicaId coordinatorId,
      DepProposeMessage depPropose,
      List<DepVerifyMessage> depVerifies,
      Set<NewViewMessage> viewChanges) {
    super();
    this.msgType = ISOSMessageType.VC_VIEWCHANGE;
    this.sender = senderID.value();
    this.seqNum = seqNum;
    this.viewNumber = viewNumber;
    this.coordinatorId = coordinatorId;
    this.depPropose = depPropose; // FIXME Kai: should this be cloned for immutability?
    this.depVerifies = depVerifies;
    this.viewChanges = viewChanges;
  }
}
