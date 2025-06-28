package isos.message;

import isos.communication.ClientMessageWrapper;
import isos.utils.NotImplementedException;

import java.io.Serializable;

/**
 * This is the main request object that is sent from the client to the replica. Before sending, it
 * is wrapped into the {@link ClientMessageWrapper} as the payload.
 *
 * @param clientId client id
 * @param command command bytes
 * @param clientLocalTimestamp increases for each request, allows ISOS to ignore duplicates
 */
public record OrderedClientRequest(int clientId, byte[] command, long clientLocalTimestamp)
    implements ClientRequest, Serializable {

  public String calculateHash() {
    throw new NotImplementedException();
  }
}
