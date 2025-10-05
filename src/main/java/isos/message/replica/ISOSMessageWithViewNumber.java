package isos.message.replica;

import isos.utils.ViewNumber;

public interface ISOSMessageWithViewNumber extends ISOSMessage {
  ViewNumber viewNumber();
}
