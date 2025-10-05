package isos.message.replica.fast;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.fast.DepCommitMessage;
import isos.utils.ReplicaId;
import java.io.*;
import org.junit.jupiter.api.Test;

class DepCommitMessageSerializationTest {
  @Test
  void testDepCommitMessageSerialization() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ReplicaId replicaId = ReplicaId.of(2);
    String depVerifysHash = "hash789";

    DepCommitMessage original = new DepCommitMessage(
        seqNum,
        replicaId,
        depVerifysHash
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
    assertEquals(original.depVerifysHash(), deserialized.depVerifysHash());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
