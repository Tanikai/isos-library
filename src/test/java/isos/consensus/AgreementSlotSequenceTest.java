package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;

import isos.utils.ReplicaId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgreementSlotSequenceTest {

  @Test
  void testInitialSlotsEmpty() {
    ReplicaId replicaId = new ReplicaId(1);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 5);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(0, slots.size());
  }

  @Test
  void testCreateLowestUnusedSequenceNumberEntryFillsSlot() {
    ReplicaId replicaId = new ReplicaId(2);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 3);
    assertEquals(0, seq.size());
    var actualSeqNum = seq.createLowestUnusedSequenceNumberEntry(null);
    SequenceNumber expectedSeqNum = new SequenceNumber(2, 0);
    assertEquals(expectedSeqNum, actualSeqNum);
    assertEquals(1, seq.size());
    AgreementSlot slot = seq.getAgreementSlotValue(expectedSeqNum);
    assertNotNull(slot);
    assertEquals(replicaId.value(), slot.getSeqNum().replicaId());
    assertEquals(0, slot.getSeqNum().sequenceCounter());
  }

  @Test
  void testReplicaIdEnforced() {
    var seq = new AgreementSlotSequence(new ReplicaId(1), 5);
    seq.createLowestUnusedSequenceNumberEntry(null);
    var invalidSequenceNum = new SequenceNumber(42, 0);
    var invalidAgreementSlot = new AgreementSlot(invalidSequenceNum);
    assertThrowsExactly(
        InvalidReplicaIdException.class, () -> seq.putAgreementSlotValue(invalidAgreementSlot));
  }

  @Test
  void testPutAgreementSlotOutOfBounds() {
    ReplicaId replicaId = new ReplicaId(1);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 3);
    seq.createLowestUnusedSequenceNumberEntry(null);
    SequenceNumber wrongCounter = new SequenceNumber(replicaId, 2);
    AgreementSlot wrongSlot = new AgreementSlot(wrongCounter);
    assertThrows(IndexOutOfBoundsException.class, () -> seq.putAgreementSlotValue(wrongSlot));
  }

  @Test
  void testGetAgreementSlotsReadOnlyIsUnmodifiable() {
    ReplicaId replicaId = new ReplicaId(3);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 5);
    var num0 = seq.createLowestUnusedSequenceNumberEntry(null);
    var num1 = seq.createLowestUnusedSequenceNumberEntry(null);
    var num2 = seq.createLowestUnusedSequenceNumberEntry(null);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(3, slots.size());
    assertEquals(num0, slots.get(0).getSeqNum());
    assertEquals(num1, slots.get(1).getSeqNum());
    assertEquals(num2, slots.get(2).getSeqNum());
    assertThrows(UnsupportedOperationException.class, () -> slots.set(0, null));
  }

  @Test
  void testBatchCreateSequenceNumberUntilFillsSlots() {
    ReplicaId replicaId = new ReplicaId(3);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 10);
    SequenceNumber seqNum = new SequenceNumber(3, 3); // creates slots from 0-3 -> size 4
    seq.batchCreateSequenceNumberUntil(seqNum);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(4, slots.size());
    for (int i = 0; i < 4; i++) {
      AgreementSlot slot = slots.get(i);
      assertNotNull(slot);
      assertEquals(replicaId.value(), slot.getSeqNum().replicaId());
      assertEquals(i, slot.getSeqNum().sequenceCounter());
    }
  }

  @Test
  void testPutAgreementSlotValueUpdatesSlot() {
    ReplicaId replicaId = new ReplicaId(5);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 5);
    seq.batchCreateSequenceNumberUntil(new SequenceNumber(3, 2)); // creates 3 slots
    SequenceNumber sn = new SequenceNumber(replicaId, 1);
    AgreementSlot newSlot = new AgreementSlot(sn);
    seq.putAgreementSlotValue(newSlot);
    List<AgreementSlot> slots = seq.getAgreementSlotsReadOnly();
    assertEquals(newSlot, slots.get(1));
  }

  @Test
  void doNotOverwriteExistingWhenBatchCreateUntil() {
    ReplicaId replicaId = new ReplicaId(1);
    AgreementSlotSequence seq = new AgreementSlotSequence(replicaId, 10);
    var firstSeqEntry = seq.createLowestUnusedSequenceNumberEntry(null);
    AgreementSlot firstEntryExpected =
        new AgreementSlot(
            firstSeqEntry,
                null, //
                null, //
                null, //
                AgreementSlotPhase.FP_COMMITTED, //
                null,
                null,
                null);
    seq.putAgreementSlotValue(firstEntryExpected);

    var newSeqNum = new SequenceNumber(replicaId, 3); // create from 1-3
    seq.batchCreateSequenceNumberUntil(newSeqNum);

    assertEquals(4, seq.size());

    var firstEntryActual = seq.getAgreementSlotValue(firstSeqEntry);
    assertEquals(firstEntryExpected, firstEntryActual);
  }
}
