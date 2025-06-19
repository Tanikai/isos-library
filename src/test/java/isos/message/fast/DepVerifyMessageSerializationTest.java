package isos.message.fast;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import java.io.*;
import org.junit.jupiter.api.Test;

class DepVerifyMessageSerializationTest {
  @Test
  void testDepVerifyMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ReplicaId followerId = new ReplicaId(2);
    String depProposeHash = "hash456";
    DependencySet depSet = new DependencySet();

    DepVerifyMessage original = new DepVerifyMessage(
        seqNum,
        followerId,
        depProposeHash,
        depSet
    );

    byte[] bytes;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(original);
      out.flush();
      bytes = bos.toByteArray();
    }

    DepVerifyMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (DepVerifyMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.followerId(), deserialized.followerId());
    assertEquals(original.depProposeHash(), deserialized.depProposeHash());
    assertEquals(original.depSet(), deserialized.depSet());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
