package isos.communication;

import bftsmart.communication.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * This wrapper class is used to store all metadata that is required to send this message. It is
 * adapted from {@link bftsmart.tom.core.messages.TOMMessage}, but generalized, i.e., without the
 * BFT-SMaRt-specific consensus aspects. It can be sent from Client->Replica or Replica->Client.
 *
 * <p>The actual contents are contained in the {@link #payload} field. It is a general class, i.e.
 * the communication system has no information about the semantics of the payload or the used
 * consensus algorithm. It is used both on the client and replica side. In ISOS, the payload is
 * {@link isos.message.OrderedClientRequest} for C->R messages and {@link
 * isos.message.OrderedClientReply} for R->C messages.
 */
public class ClientMessageWrapper extends SystemMessage
    implements Externalizable, Comparable<ClientMessageWrapper>, Cloneable {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  // actual contents of the message
  // sender: in SystemMessage
  private byte[] payload;
  private int clientSession; // client session, defined by client one during start
  private int clientSequence; // client sequence number, increases by 1 with each message sent

  // Cache / Temporary fields used for transmitting the message
  // the bytes received from the client and its MAC and signature
  public transient boolean signed = false; // is this message signed?
  public transient byte[] serializedMessage = null;
  public transient byte[] serializedMessageSignature = null;
  public transient int destination = -1; // message destination
  public transient int retry = 4;

  // TODO: do I need replyServer?
  // TODO: Information about whether the message originated from client or server

  public transient ClientMessageWrapper reply; // reply associated with this message

  public ClientMessageWrapper() {}

  public ClientMessageWrapper(int sender, int clientSession, int clientSequence, byte[] payload) {
    this.sender = sender;
    this.clientSession = clientSession;
    this.clientSequence = clientSequence;
    this.payload = payload;
  }

  public int getClientSequence() {
    return this.clientSequence;
  }

  public int getClientSession() {
    return this.clientSession;
  }

  public byte[] getPayload() {
    return this.payload;
  }

  public void setPayload(byte[] p) {
    this.payload = p;
  }

  @Override
  public ClientMessageWrapper clone() {
    ClientMessageWrapper clone =
        new ClientMessageWrapper(sender, clientSession, clientSequence, payload);

    clone.signed = this.signed;
    clone.serializedMessage = this.serializedMessage;
    clone.serializedMessageSignature = this.serializedMessageSignature;
    clone.destination = this.destination;
    clone.retry = this.retry;

    return clone;
  }

  /**
   * Writes to a DataOutput, not an ObjectOutput.
   *
   * @param out
   * @throws IOException
   */
  public void wExternal(DataOutput out) throws IOException {
    out.writeInt(sender);
    // viewID -> not relevant for ISOS
    // msgType -> should be in payload
    out.writeInt(clientSession);
    out.writeInt(clientSequence);

    if (payload == null) {
      out.writeInt(-1);
    } else {
      out.writeInt(payload.length);
      out.write(payload);
    }
  }

  public void rExternal(DataInput in) throws IOException {
    this.sender = in.readInt();
    this.clientSession = in.readInt();
    this.clientSequence = in.readInt();

    int toRead = in.readInt();
    if (toRead != -1) {
      this.payload = new byte[toRead];
      in.readFully(payload);
    }
  }

  /**
   * This two methods implement the Externalizable interface --- only used for serialization of
   * forwarded requests/replies when used for transferring ** replica application state ** that
   * includes these. Not used for the total order multicast protocol or the client-server
   * communication.
   */
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    wExternal(out);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    rExternal(in);
  }

  /**
   * Verifies if two ClientMessageWrappers are equal. For performance reasons, the method only
   * verifies if the send and sequence are equal.
   *
   * <p>Two TOMMessage are equal if they have the same sender, sequence number and content.
   */
  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if (!(o instanceof ClientMessageWrapper)) {
      return false;
    }

    ClientMessageWrapper mc = (ClientMessageWrapper) o;

    return (mc.getSender() == this.sender) && (mc.payload == this.payload);
    // FIXME Kai: when are two messages equal?
  }

  @Override
  public int compareTo(ClientMessageWrapper o) {

    final int BEFORE = -1;
    final int EQUAL = 0;
    final int AFTER = 1;

    if (this.equals(o)) return EQUAL;

    if (this.getSender() < o.getSender()) return BEFORE;
    if (this.getSender() > o.getSender()) return AFTER;

    // FIXME: add payload comparison as well

    return EQUAL;
  }
}
