/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated
 * in the @author tags
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.tom.core.messages;

import bftsmart.communication.SystemMessage;
import bftsmart.tom.util.DebugInfo;
import isos.communication.ClientMessageWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.slf4j.LoggerFactory;

/** This class represents a total ordered message used in BFT-SMaRt, sent by the client to a replica.
 *
 * @see ClientMessageWrapper */
public class TOMMessage extends SystemMessage implements Externalizable, Comparable, Cloneable {

  public transient long timestamp = 0; // timestamp to be used by the application
  public transient long seed = 0; // seed for the nonces
  // ******* EDUARDO END **************//
  public transient int numOfNonces = 0; // number of nonces
  public transient int destination = -1; // message destination
  public transient boolean signed = false; // is this message signed?
  public transient long receptionTime; // the reception time of this message (nanoseconds)
  public transient long receptionTimestamp; // the reception timestamp of this message (miliseconds)
  public transient boolean timeout = false; // this message was timed out?
  public transient boolean recvFromClient =
      false; // Did the client already sent this message to me, or did it arrived in the batch?
  public transient boolean isValid = false; // Was this request already validated by the replica?
  // the bytes received from the client and its MAC and signature
  public transient byte[] serializedMessage = null;
  public transient byte[] serializedMessageSignature = null;
  public transient byte[] serializedMessageMAC = null;
  // for benchmarking purposes
  public transient long consensusStartTime = 0; // time the consensus is created
  public transient long proposeReceivedTime = 0; // time the propose is received
  public transient long writeSentTime = 0; // time the replica's write message is sent
  public transient long acceptSentTime = 0; // time the replica's accept message is sent
  public transient long decisionTime = 0; // time the decision is established
  public transient long deliveryTime = 0; // time the request is delivered
  public transient long executedTime = 0; // time the request is executed
  // the reply associated with this message
  public transient TOMMessage reply = null;
  public transient boolean alreadyProposed = false;
  public transient int retry = 4;
  // ******* EDUARDO BEGIN **************//
  private int viewID; // current sender view
  private TOMMessageType type; // request type: application or reconfiguration request
  private int session; // Sequence number defined by the client
  // Sequence number defined by the client.
  // There is a sequence number for ordered and another for unordered messages
  private int sequence;
  private int operationId; // Sequence number defined by the client
  private byte[] content = null; // Content of the message
  // the fields bellow are not serialized!!!
  private transient int id; // ID for this message. It should be unique
  private int replyServer = -1;

  /** THIS IS JOAO'S CODE, FOR DEBUGGING */
  private transient DebugInfo info = null; // Debug information

  /**
   * Creates a new instance of TOMMessage
   *
   * @param sender ID of the process which sent the message
   * @param session Session id of the sender
   * @param sequence Sequence number defined by the client
   * @param content Content of the message
   * @param view ViewId of the message
   * @param type Type of the request
   */
  // public TOMMessage(int sender, int session, int sequence, byte[] content, int view,
  // TOMMessageType type) {
  //	this(sender, session, sequence, -1, content, view, type);
  // }

  public TOMMessage() {}

  /**
   * Creates a new instance of TOMMessage. This one has an operationId parameter used for FIFO
   * executions
   *
   * @param sender The client id
   * @param session The session id of the sender
   * @param sequence The sequence number created based on the message type
   * @param operationId The operation sequence number disregarding message type
   * @param content The command to be executed
   * @param view The view in which the message was sent
   * @param type Ordered or Unordered request
   */
  public TOMMessage(
      int sender,
      int session,
      int sequence,
      int operationId,
      byte[] content,
      int view,
      TOMMessageType type) {
    super(sender);
    this.session = session;
    this.sequence = sequence;
    this.operationId = operationId;
    this.viewID = view;
    buildId();
    this.content = content;
    this.type = type;
  }

  /**
   * Retrieves the process ID of the sender given a message ID
   *
   * @param id Message ID
   * @return Process ID of the sender
   */
  public static int getSenderFromId(int id) {
    return id >>> 20;
  }

  public static byte[] messageToBytes(TOMMessage m) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    try {
      m.wExternal(dos);
      dos.flush();
    } catch (Exception e) {

    }
    return baos.toByteArray();
  }

  /****************************************************/

  public static TOMMessage bytesToMessage(byte[] b) {
    ByteArrayInputStream bais = new ByteArrayInputStream(b);
    DataInputStream dis = new DataInputStream(bais);

    TOMMessage m = new TOMMessage();
    try {
      m.rExternal(dis);
    } catch (Exception e) {
      LoggerFactory.getLogger(TOMMessage.class).error("Failed to deserialize TOMMessage", e);
      return null;
    }

    return m;
  }

  /**
   * Retrieves the debug info from the TOM layer
   *
   * @return The debug info from the TOM layer
   */
  public DebugInfo getDebugInfo() {
    return info;
  }

  /** Retrieves the debug info from the TOM layer */
  public void setDebugInfo(DebugInfo info) {
    this.info = info;
  }

  /**
   * Retrieves the session id of this message
   *
   * @return The session id of this message
   */
  public int getSession() {
    return session;
  }

  /**
   * Retrieves the sequence number defined by the client
   *
   * @return The sequence number defined by the client
   */
  public int getSequence() {
    return sequence;
  }

  public int getOperationId() {
    return operationId;
  }

  public int getViewID() {
    return viewID;
  }

  public TOMMessageType getReqType() {
    return type;
  }

  /**
   * Retrieves the ID for this message. It should be unique
   *
   * @return The ID for this message.
   */
  public int getId() {
    return id;
  }

  /**
   * Retrieves the content of the message
   *
   * @return The content of the message
   */
  public byte[] getContent() {
    return content;
  }

  /**
   * Verifies if two TOMMessage are equal. For performance reasons, the method only verifies if the
   * send and sequence are equal.
   *
   * <p>Two TOMMessage are equal if they have the same sender, sequence number and content.
   */
  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if (!(o instanceof TOMMessage)) {
      return false;
    }

    TOMMessage mc = (TOMMessage) o;

    return (mc.getSender() == sender)
        && (mc.getSequence() == sequence)
        && (mc.getOperationId() == operationId);
  }

  @Override
  public int hashCode() {
    /*int hash = 5;
    hash = 59 * hash + this.sequence;
    hash = 59 * hash + this.getSender();
    hash = 59 * hash + this.getOperationId();*/
    return this.id;
  }

  @Override
  public String toString() {
    return "[" + sender + ":" + session + ":" + sequence + "]";
  }

  public void wExternal(DataOutput out) throws IOException {
    out.writeInt(sender);
    out.writeInt(viewID);
    out.writeByte(type.ordinal());
    out.writeInt(session);
    out.writeInt(sequence);
    out.writeInt(operationId);
    out.writeInt(replyServer);

    if (content == null) {
      out.writeInt(-1);
    } else {
      out.writeInt(content.length);
      out.write(content);
    }
  }

  public void rExternal(DataInput in) throws IOException, ClassNotFoundException {
    sender = in.readInt();
    viewID = in.readInt();
    type = TOMMessageType.getMessageType(in.readByte());
    session = in.readInt();
    sequence = in.readInt();
    operationId = in.readInt();
    replyServer = in.readInt();

    int toRead = in.readInt();
    if (toRead != -1) {
      content = new byte[toRead];
      in.readFully(content);
    }

    buildId();
  }

  /** Used to build an unique id for the message */
  private void buildId() {
    // id = (sender << 20) | sequence;
    int hash = 5;
    hash = 59 * hash + this.getSender();
    hash = 59 * hash + this.sequence;
    hash = 59 * hash + this.session;
    id = hash;
  }

  @Override
  public int compareTo(Object o) {
    final int BEFORE = -1;
    final int EQUAL = 0;
    final int AFTER = 1;

    TOMMessage tm = (TOMMessage) o;

    if (this.equals(tm)) return EQUAL;

    if (this.getSender() < tm.getSender()) return BEFORE;
    if (this.getSender() > tm.getSender()) return AFTER;

    if (this.getSession() < tm.getSession()) return BEFORE;
    if (this.getSession() > tm.getSession()) return AFTER;

    if (this.getSequence() < tm.getSequence()) return BEFORE;
    if (this.getSequence() > tm.getSequence()) return AFTER;

    if (this.getOperationId() < tm.getOperationId()) return BEFORE;
    if (this.getOperationId() > tm.getOperationId()) return AFTER;

    return EQUAL;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    TOMMessage clone =
        new TOMMessage(sender, session, sequence, operationId, content, viewID, type);

    clone.setReplyServer(replyServer);

    clone.acceptSentTime = this.acceptSentTime;
    clone.alreadyProposed = this.alreadyProposed;
    clone.authenticated = this.authenticated;
    clone.consensusStartTime = this.consensusStartTime;
    clone.decisionTime = this.decisionTime;
    clone.deliveryTime = this.deliveryTime;
    clone.destination = this.destination;
    clone.executedTime = this.executedTime;
    clone.info = this.info;
    clone.isValid = this.isValid;
    clone.numOfNonces = this.numOfNonces;
    clone.proposeReceivedTime = this.proposeReceivedTime;
    clone.receptionTime = this.receptionTime;
    clone.receptionTimestamp = this.receptionTimestamp;
    clone.recvFromClient = this.recvFromClient;
    clone.reply = this.reply;
    clone.seed = this.seed;
    clone.serializedMessage = this.serializedMessage;
    clone.serializedMessageMAC = this.serializedMessageMAC;
    clone.serializedMessageSignature = this.serializedMessageSignature;
    clone.signed = this.signed;
    clone.timeout = this.timeout;
    clone.timestamp = this.timestamp;
    clone.writeSentTime = this.writeSentTime;
    clone.retry = this.retry;

    return clone;
  }

  public void setSender(int sender) {
    this.sender = sender;
  }

  public int getReplyServer() {
    return replyServer;
  }

  public void setReplyServer(int replyServer) {
    this.replyServer = replyServer;
  }

  /**
   * This two methods implement the Externalizable interface --- only used for serialization of
   * forwarded requests/replies when used for transferring ** replica application state ** that
   * includes these. Not used for the total order multicast protocol or the client-server
   * communication.
   */
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    this.wExternal(out);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    this.rExternal(in);
  }
}
