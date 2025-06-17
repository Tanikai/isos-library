package isos.message;

import bftsmart.communication.SystemMessage;
import isos.utils.NotImplementedException;

/**
 * Request Message sent from client. Data class.
 */
public class ClientRequest extends SystemMessage {
  protected int clientId;
  protected byte[] command;
  protected long
      clientLocalTimestamp; // increases for each request, allows ISOS to ignore duplicates

  public ClientRequest(int senderId, int clientId, byte[] command, long clientLocalTimestamp) {
    this.sender = senderId;
    this.clientId = clientId;
    this.command = command;
    this.clientLocalTimestamp = clientLocalTimestamp;
  }

  public String calculateHash() {
    throw new NotImplementedException();
  }
}
