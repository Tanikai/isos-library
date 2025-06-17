package isos.consensus;

import isos.message.ClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * Single agreement slot. Used solely as a data class. Logic is contained in the
 * AgreementSlotSequence and the AgreementSlotManager.
 */
public record AgreementSlot(
    // DECISION Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in
    // the AgreementSlotSequence? -> only stored in the sequence, so that coordination does not have
    // to be
    SequenceNumber seqNum, // s_j: Agreement Slot s_j
    ClientRequest request, // Contains client ID, client-local timestamp, and command
    DepProposeMessage depPropose, // p: DepPropose for slot s_j includes fast path quorum F
    Map<ReplicaId, DepVerifyMessage>
        depVerifies, // v[f_i]: DepVerify  for slot s_j from follower f_i
    AgreementSlotPhase step, // current phase
    ViewNumber viewNumber, // View number for slot s_j, initially -1
    Map<ReplicaId, ViewNumber>
        peerViewNumbers // Highest view number for slot s_j seen for replica r_i
    // certificate
    ) {


  public AgreementSlot(SequenceNumber seqNum) {
    this(seqNum, null, null, new HashMap<>(), AgreementSlotPhase.NULL, new ViewNumber(), new HashMap<>());
  }
}
