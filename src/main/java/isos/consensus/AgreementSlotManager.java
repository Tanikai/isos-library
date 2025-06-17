package isos.consensus;

import isos.message.ISOSMessage;
import isos.utils.ReplicaId;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * This class maintains an AgreementSlotSequence for each replica. Additionally, it pre-sorts
 * incoming messages according to their sequence number so that agreement slot-specific worker
 * threads
 */
public class AgreementSlotManager {

  private ConcurrentMap<ReplicaId, AgreementSlotSequence> replicaAgreementSlots;

  /** Use priority queue and sort by lowest sequence number. */
  private Map<SequenceNumber, Queue<ISOSMessage>> incomingMsgs; //

  public AgreementSlotManager() {
    this.replicaAgreementSlots = new ConcurrentHashMap<>();
  }

  public SequenceNumber createLowestUnusedSequenceNumberEntry(ReplicaId replicaId) {
    return this.replicaAgreementSlots.get(replicaId).createLowestUnusedSequenceNumberEntry();
  }

  public Map<ReplicaId, List<AgreementSlot>> getUsedAgreementSlots() {
    return replicaAgreementSlots.entrySet().stream().
    collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAgreementSlotsReadOnly()));
  }
}
