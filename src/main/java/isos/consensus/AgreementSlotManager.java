package isos.consensus;

import isos.message.ISOSMessage;

import java.util.Map;
import java.util.PriorityQueue;

/**
 * This class manages the agreement slots. Additionally, it pre-sorts incoming messages according to
 * their sequence number so that agreement slot-specific worker threads
 */
public class AgreementSlotManager {

  private AgreementSlotSequence agreementSlots;
  /**
   * Use priority queue and sort by lowest sequence number.
   */
  private Map<SequenceNumber, PriorityQueue<ISOSMessage>> incomingMsgs; //
}
