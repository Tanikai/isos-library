package isos.message.reconciliation;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.*;
import org.junit.jupiter.api.Test;

class CommitMessageSerializationTest {
  @Test
  void testCommitMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId replicaId = new ReplicaId(2);
    String depVerifiesHash = "hashCommit";

    CommitMessage original = new CommitMessage(
        seqNum,
        viewNumber,
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

    CommitMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (CommitMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.replicaId(), deserialized.replicaId());
    assertEquals(original.depVerifiesHash(), deserialized.depVerifiesHash());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
