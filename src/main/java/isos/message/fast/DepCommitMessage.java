package isos.message.fast;

import isos.consensus.SequenceNumber;
import isos.message.ISOSMessage;
import isos.message.ISOSMessageType;
import isos.utils.ReplicaId;

public class DepCommitMessage extends ISOSMessage {
  // Message Fields:
  // msgType in parent
  // s_i: agreement slot, in parent
  private ReplicaId replicaId; // r_i: Replica ID of sender
  private String depVerifiesHash; // h(vec{dv}): Hash of DepVerifies

  public DepCommitMessage(
      ReplicaId senderId, SequenceNumber seqNum, ReplicaId replicaId, String depVerifiesHash) {
    super();
    this.msgType = ISOSMessageType.DEP_COMMIT;
    this.sender = senderId.value();
    this.seqNum = seqNum;
    this.replicaId = replicaId;
    this.depVerifiesHash = depVerifiesHash;
  }
}
