package isos.message.replica.viewchange;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestContainer;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NewViewMessageSerializationTest {
  @Test
  void testNewViewMessageSerialization() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ViewNumber viewNumber = ViewNumber.of(5);
    ReplicaId replicaId = ReplicaId.of(2);
    ReplicaId coordinatorId = ReplicaId.of(1);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hashViewChange", new DependencySet(), new HashSet<>());
    OrderedClientRequest req = new OrderedClientRequest(1, "test123".getBytes(), 0);
    var container = new ClientRequestContainer(List.of(req));
    DepProposeWithRequest dp = new DepProposeWithRequest(depPropose, container);
    List<DepVerifyMessage> depVerifys = new ArrayList<>();
    depVerifys.add(new DepVerifyMessage(seqNum, ReplicaId.of(3), "hash", new DependencySet()));
    Set<ViewChangeMessage> viewChanges = new HashSet<>();
    viewChanges.add(new ViewChangeMessage(seqNum, viewNumber, ReplicaId.of(4), null));

    NewViewMessage original =
        new NewViewMessage(seqNum, viewNumber, replicaId, dp, depVerifys, viewChanges);

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
    DepProposeWithRequest dpDeserialized = deserialized.depPropose();
    assertEquals(dp.depPropose(), dpDeserialized.depPropose());
    assertEquals(dp.requests(), dpDeserialized.requests());
  }

  @Test
  void testNewViewMessageSerializationWithNullDepPropose() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ViewNumber viewNumber = ViewNumber.of(5);
    ReplicaId replicaId = ReplicaId.of(2);
    List<DepVerifyMessage> depVerifys = new ArrayList<>();
    depVerifys.add(new DepVerifyMessage(seqNum, ReplicaId.of(3), "hash", new DependencySet()));
    Set<ViewChangeMessage> viewChanges = new HashSet<>();
    viewChanges.add(new ViewChangeMessage(seqNum, viewNumber, ReplicaId.of(4), null));

    NewViewMessage original =
        new NewViewMessage(seqNum, viewNumber, replicaId, null, depVerifys, viewChanges);

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

    assertNull(deserialized.depPropose());
    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.coordinatorId(), deserialized.coordinatorId());
    assertEquals(original.depVerifys(), deserialized.depVerifys());
    assertEquals(original.viewChanges(), deserialized.viewChanges());
  }

  @Test
  void testNewViewMessageSerializationWithNullDepVerifys() throws Exception {
    SequenceNumber seqNum = SequenceNumber.of(2, 42);
    ViewNumber viewNumber = ViewNumber.of(5);
    ReplicaId replicaId = ReplicaId.of(2);
    ReplicaId coordinatorId = ReplicaId.of(1);
    DepProposeMessage depPropose =
        new DepProposeMessage(
            seqNum, coordinatorId, "hashViewChange", new DependencySet(), new HashSet<>());
    OrderedClientRequest req = new OrderedClientRequest(1, "test123".getBytes(), 0);
    var container = new ClientRequestContainer(List.of(req));
    DepProposeWithRequest dp = new DepProposeWithRequest(depPropose, container);
    Set<ViewChangeMessage> viewChanges = new HashSet<>();
    viewChanges.add(new ViewChangeMessage(seqNum, viewNumber, ReplicaId.of(4), null));

    NewViewMessage original =
        new NewViewMessage(seqNum, viewNumber, replicaId, dp, null, viewChanges);

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

    assertNull(deserialized.depVerifys());
    assertEquals(original.seqNum(), deserialized.seqNum());
    assertEquals(original.viewNumber(), deserialized.viewNumber());
    assertEquals(original.coordinatorId(), deserialized.coordinatorId());
    assertEquals(original.depPropose(), deserialized.depPropose());
    assertEquals(original.viewChanges(), deserialized.viewChanges());
    DepProposeWithRequest dpDeserialized = deserialized.depPropose();
    assertEquals(dp.depPropose(), dpDeserialized.depPropose());
    assertEquals(dp.requests(), dpDeserialized.requests());
  }
}
