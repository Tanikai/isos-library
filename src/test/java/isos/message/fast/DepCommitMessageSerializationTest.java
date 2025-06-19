package isos.message.fast;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import java.io.*;
import org.junit.jupiter.api.Test;

class DepCommitMessageSerializationTest {
  @Test
  void testDepCommitMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ReplicaId replicaId = new ReplicaId(2);
    String depVerifiesHash = "hash789";

    DepCommitMessage original = new DepCommitMessage(
        seqNum,
        replicaId,
        depVerifiesHash
    );

    byte[] bytes;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(original);
      out.flush();
      bytes = bos.toByteArray();
    }

    DepCommitMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (DepCommitMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.replicaId(), deserialized.replicaId());
    assertEquals(original.depVerifiesHash(), deserialized.depVerifiesHash());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
