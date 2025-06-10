package isos.viewchange;

/**
 * Requirement: Report the state of the agreement slot as:
 *
 * <ul>
 *   <li>Fast Path Certificate (FPC): Original DepPropose, and 2f DepVerify messages from different
 *       followers -> slot was *fp-verified*
 *   <li>Reconciliation Path Certificate (RPC): Original DepPropose, 2f DepVerify from different
 *       followers, 2f+1 Prepare (from same view) -> slot was *rp-prepared*
 *   <li>If conditions cannot be fulfilled, certificate is empty.
 * </ul>
 */
public class ViewChangeCertificate {
}
