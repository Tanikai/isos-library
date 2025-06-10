package isos.consensus;

import isos.utils.NotImplementedException;
import jdk.jshell.spi.ExecutionControl;

import java.util.Map;

/** Sequence of agreement slots. Each agreement slot is uniquely identified by a sequence number */
public class AgreementSlotSequence {
  /**
   * Requirements:
   * - O(1) for accessing slot with sequenceNumber
   * - Efficiently get the lowest unused sequence number
   *   - -> should be ordered, just get last
   * - Requirement: ...computes the dependency set containing
   *
   *   Potential Implementations:
   *   - SortedMap?
   */
  private Map<SequenceNumber, AgreementSlot> slots;
  // TODO Kai: After we've computed the dependencies of an AgreementSlot once, can we

  /**
   * Requirement: To start the fast path, the coordinator [that received a request from the client]
   * selects its agreement slot with the lowest unused sequence number (see paper sec. B).
   *
   * @return
   */
  public SequenceNumber getLowestUnusedSequenceNumber() {
    // TODO Kai: just get last filled agreement slot, and increment by 1 -> critical section, or
    // lock-free op required!

    throw new NotImplementedException();
  }
}
