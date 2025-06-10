package isos.message;

import bftsmart.communication.SystemMessage;
import isos.consensus.SequenceNumber;

/** Superclass of all ISOS messages. */
public abstract class ISOSMessage extends SystemMessage {
  protected ISOSMessageType msgType;
  protected SequenceNumber seqNum;

  public ISOSMessageType getMsgType() {
    return msgType;
  }

  public SequenceNumber getSeqNum() {
    return seqNum;
  }
}
