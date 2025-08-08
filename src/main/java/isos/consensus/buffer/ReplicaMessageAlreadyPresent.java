package isos.consensus.buffer;

import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

public class ReplicaMessageAlreadyPresent extends RuntimeException {
  public ReplicaMessageAlreadyPresent(ReplicaId sender, ISOSMessageType msgType) {
    super(String.format("Message of type %s by sender %s is already buffered", msgType, sender));
  }

  public ReplicaMessageAlreadyPresent(
      ReplicaId sender, ViewNumber viewNumber, ISOSMessageType msgType) {
    super(
        String.format(
            "Message of type %s by sender %s in view %s is already buffered",
            msgType, sender, viewNumber));
  }
}
