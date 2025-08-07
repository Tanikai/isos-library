package isos.consensus.model;

import static org.junit.jupiter.api.Assertions.*;

import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import java.util.*;
import org.junit.jupiter.api.Test;

class DepVerifyMapTest {
  @Test
  void testMinimumDependencyOccurrence_AllDependenciesMeetMinCount() {
    SequenceNumber dep1 = new SequenceNumber(1, 1);
    SequenceNumber dep2 = new SequenceNumber(2, 2);
    DependencySet set1 = new DependencySet(Set.of(dep1, dep2));
    DependencySet set2 = new DependencySet(Set.of(dep1, dep2));
    DepVerifyMessage msg1 = new DepVerifyMessage(new SequenceNumber(1, 0), new ReplicaId(1), "hash", set1);
    DepVerifyMessage msg2 = new DepVerifyMessage(new SequenceNumber(2, 0), new ReplicaId(2), "hash", set2);
    List<DepVerifyMessage> messages = List.of(msg1, msg2);
    assertTrue(DepVerifyMap.minimumDependencyOccurrence(messages, 2));
  }

  @Test
  void testMinimumDependencyOccurrence_SomeDependenciesDoNotMeetMinCount() {
    SequenceNumber dep1 = new SequenceNumber(1, 1);
    SequenceNumber dep2 = new SequenceNumber(2, 2);
    DependencySet set1 = new DependencySet(Set.of(dep1));
    DependencySet set2 = new DependencySet(Set.of(dep2));
    DepVerifyMessage msg1 = new DepVerifyMessage(new SequenceNumber(1, 0), new ReplicaId(1), "hash", set1);
    DepVerifyMessage msg2 = new DepVerifyMessage(new SequenceNumber(2, 0), new ReplicaId(2), "hash", set2);
    List<DepVerifyMessage> messages = List.of(msg1, msg2);
    assertFalse(DepVerifyMap.minimumDependencyOccurrence(messages, 2));
  }

  @Test
  void testMinimumDependencyOccurrence_EmptyMessages() {
    List<DepVerifyMessage> messages = Collections.emptyList();
    assertTrue(DepVerifyMap.minimumDependencyOccurrence(messages, 0));
    assertTrue(DepVerifyMap.minimumDependencyOccurrence(messages, 1));
    assertTrue(DepVerifyMap.minimumDependencyOccurrence(messages, 2));
  }

  @Test
  void testMinimumDependencyOccurrence_SingleDependencyMultipleMessages() {
    SequenceNumber dep1 = new SequenceNumber(1, 1);
    DependencySet set1 = new DependencySet(Set.of(dep1));
    DependencySet set2 = new DependencySet(Set.of(dep1));
    DepVerifyMessage msg1 = new DepVerifyMessage(new SequenceNumber(1, 0), new ReplicaId(1), "hash", set1);
    DepVerifyMessage msg2 = new DepVerifyMessage(new SequenceNumber(2, 0), new ReplicaId(2), "hash", set2);
    List<DepVerifyMessage> messages = List.of(msg1, msg2);
    assertTrue(DepVerifyMap.minimumDependencyOccurrence(messages, 2));
    assertFalse(DepVerifyMap.minimumDependencyOccurrence(messages, 3));
  }

  @Test
  void testMinimumDependencyOccurrence_SingleMessageInsufficientOccurrences() {
    SequenceNumber dep1 = new SequenceNumber(1, 1);
    DependencySet set1 = new DependencySet(Set.of(dep1));
    DepVerifyMessage msg1 = new DepVerifyMessage(new SequenceNumber(1, 0), new ReplicaId(1), "hash", set1);
    List<DepVerifyMessage> messages = List.of(msg1);
    assertFalse(DepVerifyMap.minimumDependencyOccurrence(messages, 2));
  }
}
