package isos.consensus;

import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import jdk.jshell.spi.ExecutionControl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sequence of agreement slots, for a *single* replica. Each agreement slot is uniquely identified
 * by a sequence number. Allows for concurrent access on independent (!) keys.
 */
public class AgreementSlotSequence {
  public final int AGREEMENTSLOT_SEQUENCE_SIZE = 1000;

  /**
   * Requirements:
   *
   * <ul>
   *   <li>O(1) time for accessing slot with sequenceNumber
   *   <li>Efficiently get the lowest unused sequence number of any replica -> ordered, get last?
   *   <li>Requirement: ...computes the dependency set containing sequence numbers of requests that
   *       conflict with request r.
   *   <li>Concurrent access for independent keys
   *   <li>Two-part key: ReplicaId, and sequence number
   *   <li>Return only used agreement slots
   * </ul>
   *
   * <p>Potential Implementations: - SortedMap?
   */
  private final ReplicaId replicaId;

  private AgreementSlot[] slots;

  private int size;
  private Lock addEntryLock;

  public AgreementSlotSequence(ReplicaId replicaId) {
    this.replicaId = replicaId; // required to return SequenceNumber
    this.slots = new AgreementSlot[AGREEMENTSLOT_SEQUENCE_SIZE];
    this.size = 0;
    this.addEntryLock = new ReentrantLock();
  }

  // TODO Kai: After we've computed the dependencies of an AgreementSlot once, can we

  /**
   * Requirement: To start the fast path, the coordinator [that received a request from the client]
   * selects its agreement slot with the lowest unused sequence number (see paper sec. B).
   *
   * <p>Can only get own lowest sequence number, not of other replicas
   *
   * @return Sequence number of newly created entry
   */
  public SequenceNumber createLowestUnusedSequenceNumberEntry() {
    try {
      this.addEntryLock.lock();
      // get sequence number for new slot
      SequenceNumber result = getLowestUnusedSequenceNumber();
      // initialize new slot with default AgreementSlot record
      this.slots[result.sequenceCounter()] = new AgreementSlot(result);

      return result;
    } finally {
      this.addEntryLock.unlock();
    }
  }

  public SequenceNumber getLowestUnusedSequenceNumber() {
    return new SequenceNumber(this.replicaId.value(), size);
  }

  /**
   * Returns a read only view of the current agreement slots.
   *
   * @return
   */
  public List<AgreementSlot> getAgreementSlotsReadOnly() {
    return Collections.unmodifiableList(Arrays.asList(this.slots).subList(0, size + 1));
  }

  public void putAgreementSlotValue(AgreementSlot newValue) {
    // check whether AgreementSlot replicaId is correct
    var seqNum = newValue.seqNum();
    if (seqNum.replicaId() != this.replicaId.value()) {
      throw new IllegalArgumentException(
          String.format(
              "ReplicaId %d of argument does not match with ReplicaId %d of sequence",
              seqNum.replicaId(), this.replicaId.value()));
    }
  }
}
