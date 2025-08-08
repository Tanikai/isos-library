package isos.message.replica.viewchange;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @param seqNum Agreement slot
 * @param viewNumber New view number
 * @param coordinatorId
 * @param depPropose
 * @param depVerifys
 * @param viewChanges
 */
public record NewViewMessage(
    SequenceNumber seqNum,
    ViewNumber viewNumber,
    ReplicaId coordinatorId,
    DepProposeMessage depPropose,
    List<DepVerifyMessage> depVerifys,
    Set<ViewChangeMessage> viewChanges)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_NEWVIEW;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.coordinatorId;
  }
}
