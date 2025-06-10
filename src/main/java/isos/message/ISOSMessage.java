package isos.message;

import bftsmart.communication.SystemMessage;
import isos.consensus.SequenceNumber;
import isos.utils.NotImplementedException;

/** Superclass of all ISOS messages. */
public abstract class ISOSMessage extends SystemMessage {
  protected ISOSMessageType msgType;
  protected SequenceNumber seqNum; // agreement slot s_i

  public ISOSMessageType getMsgType() {
    return msgType;
  }

  public SequenceNumber getSeqNum() {
    return seqNum;
  }

  /**
   * Requirement: Compute the hash over client request r (e.g., for DepPropose.
   * @return
   */
  public String computeHash(ISOSMessage m) {
    throw new NotImplementedException();
  }
}
