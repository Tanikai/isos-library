package isos.message.replica.viewchange;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewViewMessageSerializationTest {
  @Test
  void testNewViewMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId replicaId = new ReplicaId(2);
    ReplicaId coordinatorId = new ReplicaId(1);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hashViewChange", new DependencySet(), new HashSet<>());
    List<DepVerifyMessage> depVerifies = new ArrayList<>();
    depVerifies.add(new DepVerifyMessage(seqNum, new ReplicaId(3), "hash", new DependencySet()));
    Set<ViewChangeMessage> viewChanges = new HashSet<>();
    viewChanges.add(new ViewChangeMessage(seqNum, viewNumber, new ReplicaId(4), null));

    NewViewMessage original =
        new NewViewMessage(seqNum, viewNumber, replicaId, depPropose, depVerifies, viewChanges);

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
    assertEquals(original.msgType(), deserialized.msgType());
    assertEquals(original.coordinatorId(), deserialized.coordinatorId());
    assertEquals(original.depPropose(), deserialized.depPropose());
    assertEquals(original.depVerifys(), deserialized.depVerifys());
    assertEquals(original.viewChanges(), deserialized.viewChanges());
  }
}
