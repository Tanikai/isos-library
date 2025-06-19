package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SequenceNumberEqualsTest {
  @Test
  void testEqualsSameValues() {
    SequenceNumber sn1 = new SequenceNumber(1, 42);
    SequenceNumber sn2 = new SequenceNumber(1, 42);
    assertEquals(sn1, sn2);
    assertEquals(sn2, sn1);
  }

  @Test
  void testNotEqualsDifferentReplicaId() {
    SequenceNumber sn1 = new SequenceNumber(1, 42);
    SequenceNumber sn2 = new SequenceNumber(2, 42);
    assertNotEquals(sn1, sn2);
    assertNotEquals(sn2, sn1);
  }

  @Test
  void testNotEqualsDifferentSequenceCounter() {
    SequenceNumber sn1 = new SequenceNumber(1, 42);
    SequenceNumber sn2 = new SequenceNumber(1, 43);
    assertNotEquals(sn1, sn2);
    assertNotEquals(sn2, sn1);
  }

  @Test
  void testEqualsItself() {
    SequenceNumber sn = new SequenceNumber(5, 99);
    assertEquals(sn, sn);
  }

  @Test
  void testNotEqualsNull() {
    SequenceNumber sn = new SequenceNumber(5, 99);
    assertNotEquals(sn, null);
  }

  @Test
  void testNotEqualsOtherClass() {
    SequenceNumber sn = new SequenceNumber(5, 99);
    String other = "not a SequenceNumber";
    assertNotEquals(sn, other);
  }
}
