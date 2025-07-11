package isos.message.fast;

import static org.junit.jupiter.api.Assertions.*;

import bftsmart.communication.SystemMessage;
import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessageWrapper;
import isos.message.OrderedClientRequest;
import isos.utils.ReplicaId;
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DepProposeMessageWrapperSerializationTest {
  @Test
  void testDepProposeMessageWrapperSerialization() throws Exception {
    // Arrange
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ReplicaId coordinatorId = new ReplicaId(2);
    String requestHash = "hash123";
    DependencySet depSet = new DependencySet();
    Set<ReplicaId> followerQuorum = new HashSet<>();
    followerQuorum.add(new ReplicaId(3));
    followerQuorum.add(new ReplicaId(4));
    OrderedClientRequest clientRequest =
        new OrderedClientRequest(99, new byte[] {1, 2, 3}, 123456L);

    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, requestHash, depSet, followerQuorum, clientRequest);
    ISOSMessageWrapper wrapper = new ISOSMessageWrapper(depPropose, coordinatorId.value());

    // Act
    byte[] data;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(wrapper);
      out.flush();
      data = bos.toByteArray();
    }

    SystemMessage sm;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis)) {
      sm = (SystemMessage) in.readObject();
    }

    assertInstanceOf(ISOSMessageWrapper.class, sm);
    ISOSMessageWrapper deserializedWrapper = (ISOSMessageWrapper) sm;
    assertInstanceOf(DepProposeMessage.class, deserializedWrapper.getPayload());
    DepProposeMessage deserialized = (DepProposeMessage) deserializedWrapper.getPayload();

    // Assert
    assertEquals(depPropose, deserialized);
    assertEquals(depPropose.seqNum(), deserialized.seqNum());
    assertEquals(depPropose.coordinatorId(), deserialized.coordinatorId());
    assertEquals(depPropose.requestHash(), deserialized.requestHash());
    assertEquals(depPropose.depSet(), deserialized.depSet());
    assertEquals(depPropose.followerQuorum(), deserialized.followerQuorum());
    assertEquals(depPropose.msgType(), deserialized.msgType());
    assertNotNull(deserialized.request());
    assertEquals(clientRequest.clientId(), deserialized.request().clientId());
    assertArrayEquals(clientRequest.command(), deserialized.request().command());
    assertEquals(
        clientRequest.clientLocalTimestamp(), deserialized.request().clientLocalTimestamp());
    assertEquals(clientRequest.calculateHash(), deserialized.request().calculateHash());
  }
}
