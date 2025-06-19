package isos.message;

import isos.consensus.SequenceNumber;

public interface ISOSMessage {
  ISOSMessageType msgType();
  SequenceNumber seqNum();
}
