package isos.consensus.model;

import static org.junit.jupiter.api.Assertions.*;

import isos.utils.ReplicaId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencySetTest {

  @Test
  void asBytes_sameContentDifferentOrder_producesSameBytes() {
    SequenceNumber sn1 = new SequenceNumber(new ReplicaId(1), 100);
    SequenceNumber sn2 = new SequenceNumber(new ReplicaId(2), 200);
    SequenceNumber sn2_1 = new SequenceNumber(new ReplicaId(2), 200);
    Set<SequenceNumber> setA = new HashSet<>(Arrays.asList(sn1, sn2));
    Set<SequenceNumber> setB = new HashSet<>(Arrays.asList(sn2_1, sn1));
    DependencySet depSetA = new DependencySet(setA);
    DependencySet depSetB = new DependencySet(setB);
    byte[] bytesA = depSetA.asBytes();
    byte[] bytesB = depSetB.asBytes();
    assertArrayEquals(bytesA, bytesB, "asBytes() should produce the same output for sets with the same elements, regardless of order");
  }

  @Test
  void asBytes_differentContent_producesDifferentBytes() {
    SequenceNumber sn1 = new SequenceNumber(new ReplicaId(1), 100);
    SequenceNumber sn2 = new SequenceNumber(new ReplicaId(2), 200);
    SequenceNumber sn3 = new SequenceNumber(new ReplicaId(3), 300);
    DependencySet depSetA = new DependencySet(sn1, sn2);
    DependencySet depSetB = new DependencySet(sn1, sn3);
    byte[] bytesA = depSetA.asBytes();
    byte[] bytesB = depSetB.asBytes();
    assertFalse(Arrays.equals(bytesA, bytesB), "asBytes() should produce different output for sets with different elements");
  }

  @Test
  void asBytes_emptySet_producesEmptyArray() {
    DependencySet depSet = new DependencySet(Set.of());
    byte[] bytes = depSet.asBytes();
    assertEquals(0, bytes.length, "asBytes() should produce an empty array for an empty set");
  }
}

