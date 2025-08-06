package isos.message.replica;

public enum ISOSMessageType {
  // Client Requests
  C_REQUEST,
  C_RESPONSE,
  // Fast Path
  DEP_PROPOSE,
  DEP_PROPOSE_WITH_REQ,
  DEP_VERIFY,
  DEP_COMMIT,
  // Reconciliation Path
  REC_PREPARE,
  REC_COMMIT,
  // View Change
  VC_VIEWCHANGE,
  VC_NEWVIEW,
  // Special Case: Timeout, only for local processing, not sent to other replicas
  TIMEOUT
}
