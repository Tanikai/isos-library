package isos.message.replica.fast;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestBatch;
import isos.utils.ReplicaId;
import java.io.*;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class DepProposeWithRequestSerializationTest {

  private DepProposeWithRequest serializeAndDeserialize(DepProposeWithRequest original)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(original);
    oos.flush();
    byte[] bytes = baos.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    ObjectInputStream ois = new ObjectInputStream(bais);
    return (DepProposeWithRequest) ois.readObject();
  }

  @Test
  void testSerializationWithRequest() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ReplicaId coordinatorId = ReplicaId.of(2);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "test123", new DependencySet(), new HashSet<>());
    int clientId = 123;
    byte[] payload = {1, 2, 3, 4, 5};
    long timestamp = 12345;
    OrderedClientRequest request = new OrderedClientRequest(clientId, payload, timestamp);
    var container = new ClientRequestBatch(List.of(request));
    DepProposeWithRequest original = new DepProposeWithRequest(depPropose, container);
    DepProposeWithRequest deserialized = serializeAndDeserialize(original);
    assertEquals(original, deserialized);
    assertNotNull(deserialized.requests());
  }

  @Test
  void testSerializationWithNullRequest() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ReplicaId coordinatorId = ReplicaId.of(2);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "test123", new DependencySet(), new HashSet<>());
    DepProposeWithRequest original = new DepProposeWithRequest(depPropose, null);
    DepProposeWithRequest deserialized = serializeAndDeserialize(original);
    assertEquals(original, deserialized);
    assertNull(deserialized.requests());
  }
}
