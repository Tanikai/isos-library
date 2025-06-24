package isos.consensus;

import isos.utils.ReplicaId;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sequence of agreement slots, for a *single* replica. Each agreement slot is uniquely identified
 * by a sequence number. Allows for concurrent access on independent (!) keys.
 */
public class AgreementSlotSequence {
  public static final int AGREEMENTSLOT_SEQUENCE_LENGTH = 1000;

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
   */
  private final ReplicaId replicaId;

  private final AgreementSlot[] slots;

  private int size;
  private final int length;
  private Lock addEntryLock;

  public AgreementSlotSequence(ReplicaId replicaId) {
    this(replicaId, AGREEMENTSLOT_SEQUENCE_LENGTH);
  }

  public AgreementSlotSequence(ReplicaId replicaId, int length) {
    this.replicaId = replicaId; // required to return SequenceNumber
    this.slots = new AgreementSlot[length];
    this.size = 0;
    this.length = length;
    this.addEntryLock = new ReentrantLock();
  }

  private void createDefaultEntry(SequenceNumber seqNum) {
    this.slots[seqNum.sequenceCounter()] = new AgreementSlot(seqNum);
  }

  public int size() {
    return this.size;
  }

  /**
   * Requirement: To start the fast path, the coordinator [that received a request from the client]
   * selects its agreement slot with the lowest unused sequence number (see paper sec. B).
   *
   * @return Sequence number of newly created entry
   */
  public SequenceNumber createLowestUnusedSequenceNumberEntry() {
    try {
      this.addEntryLock.lock();
      // get sequence number for new slot
      SequenceNumber result = getLowestUnusedSequenceNumber();
      // initialize new slot with default AgreementSlot record
      createDefaultEntry(result);
      size++;

      return result;
    } finally {
      this.addEntryLock.unlock();
    }
  }

  /**
   * Initializes the agreement slots Used for sequences of *other* replicas. To create new sequence
   * numbers of the *current* replica, see {@see createLowestUnusedSequenceNumberEntry}.
   *
   * @param seqNum Target sequence number (inclusive)
   * @return List of newly created sequence Numbers
   */
  public List<SequenceNumber> batchCreateSequenceNumberUntil(SequenceNumber seqNum) {
    try {
      this.addEntryLock.lock();
      SequenceNumber start = getLowestUnusedSequenceNumber();
      var resultList = new LinkedList<SequenceNumber>();

      for (int i = start.sequenceCounter(); i < seqNum.sequenceCounter() + 1; i++) {
        var newSeqNum = new SequenceNumber(this.replicaId.value(), i);
        createDefaultEntry(newSeqNum);
        resultList.add(newSeqNum);
        size++;
      }

      return resultList;
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
    if (this.size == 0) {
      return Collections.unmodifiableList(new LinkedList<>());
    }

    return Collections.unmodifiableList(Arrays.asList(this.slots).subList(0, size));
  }

  /**
   * Address of the newValue is already contained in its sequenceNumber.
   *
   * @param newValue
   */
  public void putAgreementSlotValue(AgreementSlot newValue) {
    // check whether AgreementSlot replicaId is correct
    var seqNum = newValue.seqNum();
    if (seqNum.replicaId() != this.replicaId.value()) {
      throw new InvalidReplicaIdException(
          String.format(
              "ReplicaId %d of argument does not match with ReplicaId %d of sequence",
              seqNum.replicaId(), this.replicaId.value()));
    }

    if (seqNum.sequenceCounter() > size) {
      throw new IndexOutOfBoundsException(
          String.format("SequenceNumber %d was not yet initialized", seqNum.sequenceCounter()));
    }

    this.slots[seqNum.sequenceCounter()] = newValue;
  }

  public AgreementSlot getAgreementSlotValue(SequenceNumber seqNum) {
    if (seqNum.sequenceCounter() >= this.size) {
      throw new IndexOutOfBoundsException();
    }

    return this.slots[seqNum.sequenceCounter()];
  }
}
