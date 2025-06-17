package isos.consensus;


import isos.message.ClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.message.fast.DepVerifyMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;

import java.util.Map;

/**
 * Single agreement slot. Used solely as a data class. Logic is contained in the
 * AgreementSlotSequence and the AgreementSlotManager.
 */
public record AgreementSlot(
    SequenceNumber seqNum, // s_j: Agreement Slot s_j
    ClientRequest request, // Contains client ID, client-local timestamp, and command
    DepProposeMessage depPropose, // p: DepPropose for slot s_j includes fast path quorum F
    Map<SequenceNumber, DepVerifyMessage>
        depVerifies, // v[f_i]: DepVerify  for slot s_j from follower f_i
    AgreementSlotPhase step, // current phase
    ViewNumber viewNumber, // View number for slot s_j, initially -1
    Map<ReplicaId, ViewNumber>
        peerViewNumbers // Highest view number for slot s_j seen for replica r_i
    // certificate
    ) {
  // DECISION Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in
  // the AgreementSlotSequence?
}
