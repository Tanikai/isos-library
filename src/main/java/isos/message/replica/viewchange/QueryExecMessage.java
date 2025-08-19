package isos.message.replica.viewchange;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;

import java.io.Serializable;

public record QueryExecMessage(SequenceNumber seqNum, ReplicaId replicaId)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_QUERYEXEC;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
