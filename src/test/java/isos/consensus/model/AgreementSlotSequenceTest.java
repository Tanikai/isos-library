package isos.consensus.model;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.InvalidReplicaIdException;
import isos.utils.ReplicaId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgreementSlotSequenceTest {

  @Test
  void testInitialSlotsEmpty() {
    ReplicaId replicaId = ReplicaId.of(1);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 5);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(0, slots.size());
  }

  @Test
  void testCreateLowestUnusedSequenceNumberEntryFillsSlot() {
    ReplicaId replicaId = ReplicaId.of(2);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 3);
    assertEquals(0, seq.getLowestUninitialized());
    var actualSeqNum = seq.createLowestSeqNumEntry(null);
    SequenceNumber expectedSeqNum = SequenceNumber.of(2, 0);
    assertEquals(expectedSeqNum, actualSeqNum);
    assertEquals(1, seq.getLowestUninitialized());
    AgreementSlot slot = seq.getAgreementSlotValue(expectedSeqNum);
    assertNotNull(slot);
    assertEquals(replicaId.value(), slot.getSeqNum().replicaId());
    assertEquals(0, slot.getSeqNum().sequenceCounter());
  }

  @Test
  void testReplicaIdEnforced() {
    var seq = new AgreementSlotSequence(ReplicaId.of(1), 5);
    seq.createLowestSeqNumEntry(null);
    var invalidSequenceNum = SequenceNumber.of(42, 0);
    var invalidAgreementSlot = new AgreementSlot(invalidSequenceNum);
    assertThrowsExactly(
        InvalidReplicaIdException.class, () -> seq.putAgreementSlotValue(invalidAgreementSlot));
  }

  @Test
  void testPutAgreementSlotOutOfBounds() {
    ReplicaId replicaId = ReplicaId.of(1);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 3);
    seq.createLowestSeqNumEntry(null);
    SequenceNumber wrongCounter = SequenceNumber.of(replicaId, 2);
    AgreementSlot wrongSlot = new AgreementSlot(wrongCounter);
    assertThrows(IndexOutOfBoundsException.class, () -> seq.putAgreementSlotValue(wrongSlot));
  }

  @Test
  void testGetAgreementSlotsReadOnlyIsUnmodifiable() {
    ReplicaId replicaId = ReplicaId.of(3);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 5);
    var num0 = seq.createLowestSeqNumEntry(null);
    var num1 = seq.createLowestSeqNumEntry(null);
    var num2 = seq.createLowestSeqNumEntry(null);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(3, slots.size());
    assertEquals(num0, slots.get(0).getSeqNum());
    assertEquals(num1, slots.get(1).getSeqNum());
    assertEquals(num2, slots.get(2).getSeqNum());
    assertThrows(UnsupportedOperationException.class, () -> slots.set(0, null));
  }
}
