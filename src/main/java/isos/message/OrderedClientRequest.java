package isos.message;

import isos.communication.ClientMessageWrapper;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

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

  public OrderedClientRequest {
    // Compact constructor
    Objects.requireNonNull(command);
  }

  /**
   * Calculate the hash of the client request. Used in {@link isos.message.fast.DepProposeMessage}.
   * Uses SHA-256 hashing, which is enough for this use case.
   *
   * @return
   */
  @Override
  public String calculateHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(ByteBuffer.allocate(4).putInt(clientId).array());
      digest.update(command);
      digest.update(ByteBuffer.allocate(8).putLong(clientLocalTimestamp).array());

      byte[] hashBytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OrderedClientRequest that = (OrderedClientRequest) o;
    return clientId == that.clientId
        && clientLocalTimestamp == that.clientLocalTimestamp
        && Arrays.equals(command, that.command);
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(clientId);
    result = 31 * result + Arrays.hashCode(command);
    result = 31 * result + Long.hashCode(clientLocalTimestamp);
    return result;
  }
}
