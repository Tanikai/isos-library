package isos.message;

import bftsmart.communication.SystemMessage;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class acts as a container of an {@link ISOSMessage} so that the consensus messages can be
 * sent via the ServerCommunicationSystem. For this, it extends the SystemMessage class and has the
 * consensus message in the payload field.
 *
 * <p>Additionally, it can be used to communicate metadata, like the {@link
 * bftsmart.tom.core.messages.TOMMessage} of BFT-SMaRt. Examples for the metadata include
 *
 * <ul>
 *   <li>Message signatures
 *   <li>Reception timestamps
 *   <li>etc.
 * </ul>
 */
public class ISOSMessageWrapper extends SystemMessage {

  private ISOSMessage payload;

  public ISOSMessageWrapper() {}

  public ISOSMessageWrapper(ISOSMessage payload, int sender) {
    this.payload = payload;
    this.sender = sender; // sender needs to match with
  }

  public ISOSMessage getPayload() {
    return this.payload;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(this.sender);
    out.writeObject(payload);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    sender = in.readInt();
    Object obj = in.readObject();
    if (!(obj instanceof ISOSMessage)) {
      throw new IOException("Invalid payload type");
    }
    this.payload = (ISOSMessage) obj;
  }
}
