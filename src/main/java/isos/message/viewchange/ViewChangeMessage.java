package isos.message.viewchange;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.List;
import java.util.Set;

/**
 * @param sender physical sender of this message
 * @param seqNum agreement slot
 * @param viewNumber New view number
 * @param coordinatorId Newly selected coordinator of view
 * @param depPropose Original DepPropose message
 * @param depVerifies Accompanying DepVerifys
 * @param viewChanges Set of 2f+1 ViewChanges
 */
public record ViewChangeMessage(
    int sender,
    SequenceNumber seqNum,
    ViewNumber viewNumber,
    ReplicaId coordinatorId,
    DepProposeMessage depPropose,
    List<DepVerifyMessage> depVerifies,
    Set<NewViewMessage> viewChanges)
    implements ISOSMessage {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_VIEWCHANGE;
  }
}
