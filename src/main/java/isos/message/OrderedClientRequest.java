package isos.message;

import isos.utils.NotImplementedException;

import java.io.Serializable;

/**
 * Request message sent from client.
 *
 * @param clientId client id
 * @param command command bytes
 * @param clientLocalTimestamp increases for each request, allows ISOS to ignore duplicates
 */
public record OrderedClientRequest(int clientId, byte[] command, long clientLocalTimestamp) implements ClientRequest, Serializable {

  public String calculateHash() {
    throw new NotImplementedException();
  }
}
