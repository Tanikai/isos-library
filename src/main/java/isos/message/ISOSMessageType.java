package isos.message;

public enum ISOSMessageType {
  // Client Requests
  C_REQUEST,
  C_RESPONSE,
  // Fast Path
  DEP_PROPOSE,
  DEP_VERIFY,
  DEP_COMMIT,
  // Reconciliation Path
  REC_PREPARE,
  REC_COMMIT
}
