package isos.consensus;


import isos.message.ClientRequest;

/**
 * Single agreement slot.
 */
public record AgreementSlot(SequenceNumber seqNum, ClientRequest request) {
  // DECISION Kai: should SequenceNumber be stored in the AgreementSlot object as well, or only in the AgreementSlotSequence?
}
