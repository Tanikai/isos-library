package isos.message.viewchange;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.SequenceNumber;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ViewChangeMessageSerializationTest {
  @Test
  void testViewChangeMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId coordinatorId = new ReplicaId(2);
    DepProposeMessage depPropose = new DepProposeMessage(
        seqNum,
        coordinatorId,
        "hashViewChange",
        new isos.consensus.DependencySet(),
        new HashSet<>()
    );
    List<DepVerifyMessage> depVerifies = new ArrayList<>();
    depVerifies.add(new DepVerifyMessage(seqNum, new ReplicaId(3), "hash", new isos.consensus.DependencySet()));
    Set<NewViewMessage> viewChanges = new HashSet<>();
    viewChanges.add(new NewViewMessage(seqNum, viewNumber, new ReplicaId(4), null));

    ViewChangeMessage original = new ViewChangeMessage(
        seqNum,
        viewNumber,
        coordinatorId,
        depPropose,
        depVerifies,
        viewChanges
    );

    byte[] bytes;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(original);
      out.flush();
      bytes = bos.toByteArray();
    }

    ViewChangeMessage deserialized;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
         ObjectInputStream in = new ObjectInputStream(bis)) {
      deserialized = (ViewChangeMessage) in.readObject();
    }

    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.coordinatorId(), deserialized.coordinatorId());
    assertEquals(original.depPropose(), deserialized.depPropose());
    assertEquals(original.depVerifies(), deserialized.depVerifies());
    assertEquals(original.viewChanges(), deserialized.viewChanges());
    assertEquals(original.msgType(), deserialized.msgType());
  }
}
