package isos.message.replica.viewchange;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.replica.ClientRequestContainer;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;

import java.io.Serializable;

public record ExecMessage(
    SequenceNumber seqNum,
    ReplicaId replicaId,
    ClientRequestContainer clientRequests,
    DependencySet dependencySet)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.VC_EXEC;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.replicaId;
  }
}
