package isos.message.reconciliation;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.*;
import org.junit.jupiter.api.Test;

class PrepareMessageSerializationTest {
  @Test
  void testPrepareMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId replicaId = new ReplicaId(2);
    String depVerifiesHash = "hashPrepare";

    PrepareMessage original = new PrepareMessage(
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

    PrepareMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (PrepareMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.replicaId(), deserialized.replicaId());
    assertEquals(original.depVerifiesHash(), deserialized.depVerifiesHash());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
