package isos.message.fast;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DepProposeMessageHashTest {

  @Test
  void testCalculateHashConsistency() {
    SequenceNumber seqNum = new SequenceNumber(1, 42);
    ReplicaId coordinatorId = new ReplicaId(7);
    String requestHash = "abc123";
    DependencySet depSet = new DependencySet(Set.of(
        new SequenceNumber(2, 10),
        new SequenceNumber(3, 20)));
    Set<ReplicaId> followerQuorum = Set.of(new ReplicaId(5), new ReplicaId(2));

    DepProposeMessage msg1 = new DepProposeMessage(seqNum, coordinatorId, requestHash, depSet, followerQuorum);
    DepProposeMessage msg2 = new DepProposeMessage(seqNum, coordinatorId, requestHash, depSet, followerQuorum);

    String hash1 = msg1.calculateHash();
    String hash2 = msg2.calculateHash();
    assertEquals(hash1, hash2, "Hashes should be consistent for identical content");
  }

  @Test
  void testCalculateHashDifference() {
    SequenceNumber seqNum = new SequenceNumber(1, 42);
    ReplicaId coordinatorId = new ReplicaId(7);
    String requestHash = "abc123";
    DependencySet depSet = new DependencySet(Set.of(
        new SequenceNumber(2, 10),
        new SequenceNumber(3, 20)));
    Set<ReplicaId> followerQuorum = Set.of(new ReplicaId(5), new ReplicaId(2));

    DepProposeMessage msg1 = new DepProposeMessage(seqNum, coordinatorId, requestHash, depSet, followerQuorum);
    DepProposeMessage msg2 = new DepProposeMessage(seqNum, coordinatorId, "different", depSet, followerQuorum);

    String hash1 = msg1.calculateHash();
    String hash2 = msg2.calculateHash();
    assertNotEquals(hash1, hash2, "Hashes should differ for different content");
  }

  @Test
  void testCalculateHashNullFields() {
    DepProposeMessage msg1 = new DepProposeMessage(null, null, null, null, null);
    DepProposeMessage msg2 = new DepProposeMessage(null, null, null, null, null);

    String hash1 = msg1.calculateHash();
    String hash2 = msg2.calculateHash();
    assertNotNull(hash1, "Hash should not be null even if all fields are null");
    assertEquals(hash1, hash2);
  }
}

