package isos.message;

import bftsmart.communication.SystemMessage;
import isos.utils.NotImplementedException;

/**
 * Request message sent from client.
 *
 * @param clientId client id
 * @param command command bytes
 * @param clientLocalTimestamp increases for each request, allows ISOS to ignore duplicates
 */
public record ClientRequest(int clientId, byte[] command, long clientLocalTimestamp) {

  public String calculateHash() {
    throw new NotImplementedException();
  }
}
