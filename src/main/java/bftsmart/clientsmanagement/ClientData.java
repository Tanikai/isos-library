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
package bftsmart.clientsmanagement;

import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientData {

  public static final int MAX_SIZE_ORDERED_REQUESTS = 5;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  ReentrantLock clientLock = new ReentrantLock();

  private final int clientId;
  // private PublicKey publicKey = null;

  private int session = -1;

  private int lastMessageReceived = -1;
  private long lastMessageReceivedTime = 0;

  private int lastMessageDelivered = -1;

  private RequestList pendingRequests = new RequestList();
  // anb: new code to deal with client requests that arrive after their execution
  private RequestList orderedRequests = new RequestList(MAX_SIZE_ORDERED_REQUESTS);
  private RequestList replyStore = new RequestList(MAX_SIZE_ORDERED_REQUESTS);

  private Signature signatureVerificator = null;

  /**
   * Class constructor. Just store the clientId and creates a signature verificator for a given
   * client public key.
   *
   * @param clientId client unique id
   * @param publicKey client public key
   */
  public ClientData(int clientId, PublicKey publicKey) {
    this.clientId = clientId;
    if (publicKey != null) {
      try {
        signatureVerificator = TOMUtil.getSigEngine();
        signatureVerificator.initVerify(publicKey);
        logger.debug("Signature verifier initialized for client " + clientId);
      } catch (Exception ex) {
        logger.error("Failed to create client data object", ex);
      }
    }
  }

  public int getClientId() {
    return clientId;
  }

  public int getSession() {
    return session;
  }

  public void setSession(int session) {
    this.session = session;
  }

  public RequestList getPendingRequests() {
    return pendingRequests;
  }

  public RequestList getOrderedRequests() {
    return orderedRequests;
  }

  public void setLastMessageDelivered(int lastMessageDelivered) {
    this.lastMessageDelivered = lastMessageDelivered;
  }

  public int getLastMessageDelivered() {
    return lastMessageDelivered;
  }

  public void setLastMessageReceived(int lastMessageReceived) {
    this.lastMessageReceived = lastMessageReceived;
  }

  public int getLastMessageReceived() {
    return lastMessageReceived;
  }

  public void setLastMessageReceivedTime(long lastMessageReceivedTime) {
    this.lastMessageReceivedTime = lastMessageReceivedTime;
  }

  public long getLastMessageReceivedTime() {
    return lastMessageReceivedTime;
  }

  public boolean verifySignature(byte[] message, byte[] signature) {
    if (signatureVerificator != null) {
      try {
        return TOMUtil.verifySignature(signatureVerificator, message, signature);
      } catch (SignatureException ex) {
        logger.error("Failed to verify signature", ex);
      }
    }
    return false;
  }

  public boolean removeOrderedRequest(TOMMessage request) {
    if (pendingRequests.remove(request)) {
      // anb: new code to deal with client requests that arrive after their execution
      orderedRequests.addLast(request);
      return true;
    }
    return false;
  }

  public boolean removeRequest(TOMMessage request) {
    lastMessageDelivered = request.getSequence();
    boolean result = pendingRequests.remove(request);
    // anb: new code to deal with client requests that arrive after their execution
    orderedRequests.addLast(request);

    pendingRequests.removeIf(msg -> msg.getSequence() < request.getSequence());
    return result;
  }

  public TOMMessage getReply(int reqSequence) {
    TOMMessage request = orderedRequests.getBySequence(reqSequence);
    if (request != null) {
      return request.reply;
    } else {
      // if not in list of ordered requests, then check the reply store:
      return replyStore.getBySequence(reqSequence);
    }
  }

  public void addToReplyStore(TOMMessage m) {
    if (replyStore.isEmpty() || m.getSequence() > replyStore.getLast().getSequence()) {
      replyStore.addLast(m);
    } else {
      logger.debug("Reply is too old and will not be added to reply store");
    }
  }

  public TOMMessage getLastReply() {
    if (replyStore.isEmpty()) {
      logger.debug("ReplyStore is empty :: getLastReply()");
      return null;
    } else {
      return replyStore.getLast();
    }
  }

  public RequestList getReplyStore() {
    return this.replyStore;
  }
}
