package isos.message;

import bftsmart.communication.SystemMessage;
import isos.utils.NotImplementedException;

public class ClientRequest extends SystemMessage {
  protected int ClientId;
  protected byte[] command;
  protected long
      clientLocalTimestamp; // increases for each request, allows ISOS to ignore duplicates

  public String calculateHash() {
    throw new NotImplementedException();
  }
}
