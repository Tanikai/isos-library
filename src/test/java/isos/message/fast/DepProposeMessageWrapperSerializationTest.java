package isos.message.fast;

import static org.junit.jupiter.api.Assertions.*;

import bftsmart.communication.SystemMessage;
import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessageWrapper;
import isos.utils.ReplicaId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    DepProposeMessage depPropose =
        new DepProposeMessage(seqNum, coordinatorId, requestHash, depSet, followerQuorum);
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

    assertTrue(sm instanceof ISOSMessageWrapper);
    ISOSMessageWrapper deserializedWrapper = (ISOSMessageWrapper) sm;
    assertTrue(deserializedWrapper.getPayload() instanceof DepProposeMessage);
    DepProposeMessage deserialized = (DepProposeMessage) deserializedWrapper.getPayload();

    // Assert
    assertEquals(depPropose, deserialized);
    assertEquals(depPropose.seqNum(), deserialized.seqNum());
    assertEquals(depPropose.coordinatorId(), deserialized.coordinatorId());
    assertEquals(depPropose.requestHash(), deserialized.requestHash());
    assertEquals(depPropose.depSet(), deserialized.depSet());
    assertEquals(depPropose.followerQuorum(), deserialized.followerQuorum());
    assertEquals(depPropose.msgType(), deserialized.msgType());
  }
}
