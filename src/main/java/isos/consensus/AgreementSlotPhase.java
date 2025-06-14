package isos.consensus;

/**
 * Current State of Agreement Slot, see ISOS pseudocode line 5
 */
public enum AgreementSlotPhase {
  INIT,
  PROPOSED,
  // Fast Path
  FP_VERIFIED,
  FP_COMMITTED,
  // Reconciliation Path
  RP_VERIFIED,
  RP_COMMITTED,
  VIEW_CHANGE,
}
