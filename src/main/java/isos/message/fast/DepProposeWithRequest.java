package isos.message.fast;

import isos.consensus.model.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.message.OrderedClientRequest;
import isos.utils.ReplicaId;
import java.io.Serializable;
import java.util.Objects;

/**
 * A wrapper class for DepPropose that contains a ClientRequest as well. The request is able to be
 * null.
 *
 * @param depPropose
 * @param request
 */
public record DepProposeWithRequest(DepProposeMessage depPropose, OrderedClientRequest request)
    implements ISOSMessage, Serializable {

  @Override
  public SequenceNumber seqNum() {
    return this.depPropose.seqNum();
  }

  @Override
  public ReplicaId logicalSender() {
    return this.depPropose.logicalSender();
  }

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_PROPOSE_WITH_REQ;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepProposeWithRequest that = (DepProposeWithRequest) o;
    return Objects.equals(depPropose, that.depPropose) && Objects.equals(request, that.request);
  }

  @Override
  public int hashCode() {
    return Objects.hash(depPropose, request);
  }
}
