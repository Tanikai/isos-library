package isos.message.replica.viewchange;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.consensus.model.viewchange.FastPathCertificate;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewChangeMessageSerializationTest {
  @Test
  void testViewChangeMessageSerialization() throws Exception {
    SequenceNumber seqNum = new SequenceNumber(2, 42);
    ViewNumber viewNumber = new ViewNumber(5);
    ReplicaId coordinatorId = new ReplicaId(2);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hashViewChange", new DependencySet(), new HashSet<>());
    OrderedClientRequest req = new OrderedClientRequest(1, "test123".getBytes(), 0);
    DepProposeWithRequest dp = new DepProposeWithRequest(depPropose, req);
    List<DepVerifyMessage> depVerifies = new ArrayList<>();
    depVerifies.add(new DepVerifyMessage(seqNum, new ReplicaId(3), "hash", new DependencySet()));

    var fpc = new FastPathCertificate(dp, depVerifies);
    ViewChangeMessage original = new ViewChangeMessage(seqNum, viewNumber, coordinatorId, fpc);

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

    assertEquals(original, deserialized);
    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.certificate(), deserialized.certificate());
  }
}
