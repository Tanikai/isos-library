package isos.message.viewchange;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import isos.viewchange.ViewChangeCertificate;
import java.io.*;
import org.junit.jupiter.api.Test;

class NewViewMessageSerializationTest {
  @Test
  void testNewViewMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId replicaId = new ReplicaId(2);
    ViewChangeCertificate certificate = null; // Use null or a mock if needed

    NewViewMessage original = new NewViewMessage(
        seqNum,
        viewNumber,
        replicaId,
        certificate
    );

    byte[] bytes;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(original);
      out.flush();
      bytes = bos.toByteArray();
    }

    NewViewMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (NewViewMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.replicaId(), deserialized.replicaId());
    assertEquals(original.certificate(), deserialized.certificate());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
