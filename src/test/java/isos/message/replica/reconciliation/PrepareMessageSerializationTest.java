package isos.message.replica.reconciliation;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.model.SequenceNumber;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.*;
import org.junit.jupiter.api.Test;

class PrepareMessageSerializationTest {
  @Test
  void testPrepareMessageSerialization() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId replicaId = ReplicaId.of(2);
    String depVerifysHash = "hashPrepare";

    PrepareMessage original = new PrepareMessage(
        seqNum,
        viewNumber,
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

    PrepareMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (PrepareMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.replicaId(), deserialized.replicaId());
    assertEquals(original.depVerifysHash(), deserialized.depVerifysHash());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
