package isos.consensus.model;

import isos.consensus.InvalidReplicaIdException;
import isos.message.replica.ClientRequestBatch;
import isos.utils.ReplicaId;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sequence of agreement slots, for a *single* replica. Each agreement slot is uniquely identified
 * by a sequence number. Allows for concurrent access on independent (!) keys.
 */
public class AgreementSlotSequence {
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

  private int lowestUninitialized;
  private final int length;
  private final Lock addEntryLock;

  public AgreementSlotSequence(ReplicaId replicaId, int sequenceLength) {
    this.replicaId = replicaId; // required to return SequenceNumber
    this.slots = new AgreementSlot[sequenceLength];
    this.lowestUninitialized = 0;
    this.length = sequenceLength;
    this.addEntryLock = new ReentrantLock();

    for (int i = 0; i < sequenceLength; i++) {
      this.slots[i] = new AgreementSlot(SequenceNumber.of(replicaId, i));
    }
  }

  public void updateLowestUninitialized(int nextFreeSlot) {
    this.lowestUninitialized = Math.max(this.lowestUninitialized, nextFreeSlot);
  }

  public int getLowestUninitialized() {
    return this.lowestUninitialized;
  }

  public int length() {
    return this.length;
  }

  /**
   * Used when a new ClientRequest arrives to the current replica.
   *
   * <p>Requirement: To start the fast path, the coordinator [that received a request from the
   * client] selects its agreement slot with the lowest unused sequence number (see paper sec. B).
   *
   * @return Sequence number of newly created entry
   */
  public SequenceNumber createLowestSeqNumEntry(ClientRequestBatch r) {
    try {
      this.addEntryLock.lock();
      // get sequence number for new slot
      SequenceNumber result = getLowestUnusedSequenceNumber();
      // initialize new slot with default AgreementSlot record

      this.slots[result.sequenceCounter()].setRequests(r);
      lowestUninitialized++;

      return result;
    } finally {
      this.addEntryLock.unlock();
    }
  }

  private SequenceNumber getLowestUnusedSequenceNumber() {
    return SequenceNumber.of(this.replicaId.value(), lowestUninitialized);
  }

  /**
   * Returns a read only view of the current agreement slots.
   *
   * @return
   */
  public List<AgreementSlot> getAgreementSlotsReadOnly() {
    if (this.lowestUninitialized == 0) {
      return Collections.unmodifiableList(new LinkedList<>());
    }

    return Collections.unmodifiableList(Arrays.asList(this.slots).subList(0, lowestUninitialized));
  }

  /**
   * Address of the newValue is already contained in its sequenceNumber.
   *
   * @param newValue
   */
  public void putAgreementSlotValue(AgreementSlot newValue) {
    // check whether AgreementSlot replicaId is correct
    var seqNum = newValue.getSeqNum();
    if (seqNum.replicaId() != this.replicaId.value()) {
      throw new InvalidReplicaIdException(
          String.format(
              "ReplicaId %d of argument does not match with ReplicaId %d of sequence",
              seqNum.replicaId(), this.replicaId.value()));
    }

    if (seqNum.sequenceCounter() > lowestUninitialized) {
      throw new IndexOutOfBoundsException(
          String.format("SequenceNumber %d was not yet initialized", seqNum.sequenceCounter()));
    }

    this.slots[seqNum.sequenceCounter()] = newValue;
  }

  public AgreementSlot getAgreementSlotValue(SequenceNumber seqNum)
      throws IndexOutOfBoundsException {
    return this.slots[seqNum.sequenceCounter()];
  }
}
