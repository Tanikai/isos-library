package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencySetEqualsTest {
  @Test
  void testEqualsSameContent() {
    Set<SequenceNumber> set1 = new HashSet<>();
    set1.add(new SequenceNumber(1, 10));
    set1.add(new SequenceNumber(2, 20));
    DependencySet ds1 = new DependencySet(set1);

    Set<SequenceNumber> set2 = new HashSet<>();
    set2.add(new SequenceNumber(2, 20));
    set2.add(new SequenceNumber(1, 10));
    DependencySet ds2 = new DependencySet(set2);

    assertEquals(ds1, ds2);
    assertEquals(ds2, ds1);
  }

  @Test
  void testNotEqualsDifferentContent() {
    Set<SequenceNumber> set1 = new HashSet<>();
    set1.add(new SequenceNumber(1, 10));
    DependencySet ds1 = new DependencySet(set1);

    Set<SequenceNumber> set2 = new HashSet<>();
    set2.add(new SequenceNumber(2, 20));
    DependencySet ds2 = new DependencySet(set2);

    assertNotEquals(ds1, ds2);
    assertNotEquals(ds2, ds1);
  }

  @Test
  void testEqualsItself() {
    Set<SequenceNumber> set = new HashSet<>();
    set.add(new SequenceNumber(1, 10));
    DependencySet ds = new DependencySet(set);
    assertEquals(ds, ds);
  }

  @Test
  void testNotEqualsNull() {
    Set<SequenceNumber> set = new HashSet<>();
    set.add(new SequenceNumber(1, 10));
    DependencySet ds = new DependencySet(set);
    assertNotEquals(ds, null);
  }

  @Test
  void testNotEqualsOtherClass() {
    Set<SequenceNumber> set = new HashSet<>();
    set.add(new SequenceNumber(1, 10));
    DependencySet ds = new DependencySet(set);
    String other = "not a DependencySet";
    assertNotEquals(ds, other);
  }
}
