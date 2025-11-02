package isos.message.client;

import isos.communication.ClientMessageWrapper;
import isos.execution.graph.ClientPayloadDeserializer;
import isos.message.replica.fast.DepProposeMessage;

import java.io.IOException;
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
 * <p>In ISOS, it is also sent from the coordinator to the followers. This class has to contain all
 * information required to send the reply (after the command was executed) back to the client, and
 * the client should be able to determine that the response belongs to the command sent beforehand.
 */
public class OrderedClientRequest implements ClientRequest, Serializable {
  private final int clientId;
  private final byte[] command;
  private final long clientLocalTimestamp;

  private transient Object deserializedCommand = null;
  private transient ClientPayloadDeserializer deserializer;

  private static boolean deserializedCommandCacheEnabled = false;

  public OrderedClientRequest(int clientId, byte[] command, long clientLocalTimestamp) {
    Objects.requireNonNull(command);
    this.clientId = clientId;
    this.command = command;
    this.clientLocalTimestamp = clientLocalTimestamp;
  }

  public int clientId() {
    return clientId;
  }

  public byte[] command() {
    return command;
  }

  public long clientLocalTimestamp() {
    return clientLocalTimestamp;
  }

  @SuppressWarnings("unchecked")
  public <T> T getDeserializedCommand() throws IllegalStateException {
    if (OrderedClientRequest.deserializedCommandCacheEnabled) {
      if (deserializedCommand == null) {
        throw new IllegalStateException("Command has not been deserialized yet!");
      }
      return (T) deserializedCommand;
    } else {
      // This is not recommended, but included to benchmark the effect of caching the deserialized
      // command
      try {
        return (T) this.deserializer.deserializePayload(this.command);
      } catch (Exception e) {
        throw new IllegalStateException(e.getMessage());
      }
    }
  }

  public <T> void updateDeserializedCommandCache(ClientPayloadDeserializer<T> deserializer)
      throws IOException, ClassNotFoundException {
    if (OrderedClientRequest.deserializedCommandCacheEnabled) {
      this.deserializedCommand = deserializer.deserializePayload(this.command);
    } else {
      this.deserializer = deserializer;
    }
  }

  /**
   * Calculate the hash of the client request. Used in {@link DepProposeMessage}. Uses SHA-256
   * hashing, which is enough for this use case.
   *
   * @return SHA-256 hash of the request as a hex string
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

  @Override
  public String toString() {
    return "OrderedClientRequest{"
        + "clientId="
        + clientId
        + ", command="
        + Arrays.toString(command)
        + ", clientLocalTimestamp="
        + clientLocalTimestamp
        + '}';
  }

  public static void setDeserializedCommandCacheEnabled(boolean isEnabled) {
    OrderedClientRequest.deserializedCommandCacheEnabled = isEnabled;
  }
}
