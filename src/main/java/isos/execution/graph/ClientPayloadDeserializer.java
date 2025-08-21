package isos.execution.graph;

import java.io.IOException;

/**
 * Interface for lambda function that deserializes the payload from a client request. Passed in by
 * the application developer to ISOS.
 */
@FunctionalInterface
public interface ClientPayloadDeserializer<T> {
  T deserializePayload(byte[] payload) throws IOException, ClassNotFoundException;
}
