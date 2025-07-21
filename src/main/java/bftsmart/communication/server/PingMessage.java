package bftsmart.communication.server;

import bftsmart.communication.SystemMessage;
import java.io.*;

/**
 * This class is serialized and sent between replicas to determine the ping between them. Due to the
 * byzantine fault model, the payload {@link PingMessage}) has to be signed. The sender of the
 * message is contained in the payload and signed as well.
 */
public class PingMessage extends SystemMessage implements Externalizable {

  private byte[] nonce;
  private boolean isResponse;

  public PingMessage() {}

  public PingMessage(int sender, byte[] nonce, boolean isResponse) {
    this.sender = sender;
    this.nonce = nonce;
    this.isResponse = isResponse;
  }

  public byte[] getNonce() {
    return nonce;
  }

  public void setNonce(byte[] nonce) {
    this.nonce = nonce;
  }

  public boolean isResponse() {
    return isResponse;
  }

  public void setResponse(boolean response) {
    isResponse = response;
  }

  public static byte[] toByteArray(PingMessage msg) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(msg);
      return bos.toByteArray();
    }
  }

  public static PingMessage fromByteArray(byte[] buffer)
      throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      return (PingMessage) ois.readObject();
    }
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(sender);
    if (nonce != null) {
      out.writeInt(nonce.length);
      out.write(nonce);
    } else {
      out.writeInt(-1);
    }
    out.writeBoolean(isResponse);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    sender = in.readInt();
    int nonceLen = in.readInt();
    if (nonceLen >= 0) {
      nonce = new byte[nonceLen];
      in.readFully(nonce);
    } else {
      nonce = null;
    }
    isResponse = in.readBoolean();
  }
}
