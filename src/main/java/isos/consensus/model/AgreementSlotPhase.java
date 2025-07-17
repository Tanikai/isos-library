package isos.consensus.model;

/**
 * Current Step of Agreement Slot, see ISOS pseudocode line 5
 */
public enum AgreementSlotPhase {
  NULL,
  INIT,
  PROPOSED,
  // Fast Path
  FP_VERIFIED,
  FP_COMMITTED,
  // Reconciliation Path
  RP_VERIFIED,
  RP_PREPARED,
  RP_COMMITTED,
  // View Change
  VIEW_CHANGE,
}
