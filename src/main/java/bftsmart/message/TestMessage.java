package bftsmart.message;

import bftsmart.communication.SystemMessage;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class TestMessage extends SystemMessage implements Externalizable {

  // sender is already in SystemMessage
  private String msg; // message

  public TestMessage() {
    super();
  }

  public TestMessage(int sender, String msg) {
    super(sender);
    this.sender = sender;
    this.msg = msg;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(sender);
    out.writeUTF(msg);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    this.sender = in.readInt();
    this.msg = in.readUTF();
  }

  @Override
  public String toString() {
    return "TestMessage{" +
      "sender=" + sender +
      ", msg='" + msg + '\'' +
      '}';
  }
}
