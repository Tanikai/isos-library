package isos.message;

import isos.consensus.SequenceNumber;

/**
 * Interface for the record classes of ISOS messages.
 */
public interface ISOSMessage {
  ISOSMessageType msgType();
  SequenceNumber seqNum();
}
