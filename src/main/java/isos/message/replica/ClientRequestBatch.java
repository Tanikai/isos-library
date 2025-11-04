package isos.message.replica;

import isos.message.client.OrderedClientRequest;
import isos.message.replica.fast.DepProposeMessage;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;

public class ClientRequestBatch implements Serializable {
  OrderedClientRequest[] requests;

  public ClientRequestBatch(Collection<OrderedClientRequest> requests) {
    this.requests = requests.toArray(new OrderedClientRequest[0]);
  }

  public OrderedClientRequest[] getRequests() {
    return requests;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientRequestBatch that = (ClientRequestBatch) o;
    return Arrays.equals(requests, that.requests);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(requests);
  }

  /**
   * Calculate the hash of all client requests. Used in {@link DepProposeMessage}. Uses SHA-256
   * hashing, which is enough for this use case.
   *
   * @return SHA-256 hash of the request as a hex string
   */
  public String calculateHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      if (requests != null) {
        for (OrderedClientRequest req : requests) {
          if (req == null) {
            continue;
          }
          digest.update(ByteBuffer.allocate(4).putInt(req.clientId()).array());
          digest.update(req.command());
          digest.update(ByteBuffer.allocate(8).putLong(req.clientLocalTimestamp()).array());
        }
      }
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
}
