package isos.message;

import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;

/**
 * Interface for the record classes of ISOS messages.
 */
public interface ISOSMessage {
  ISOSMessageType msgType();
  SequenceNumber seqNum();

  /**
   * The logical sender of a message is the sender that created this message. The counterpart, the
   * physical sender, is in the {@link bftsmart.communication.SystemMessage}. Most of the times,
   * they are the same, there might be cases where a Replica broadcasts a message that it previously
   * received from another replica.
   *
   * @return The replica ID of the sender
   */
  ReplicaId logicalSender();
}
