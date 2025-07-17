package isos.consensus.model;

/**
 * Timeout type, see ISOS pseudocode line 9
 */
public enum ISOSTimeoutType {
  NULL,
  PROPOSE,
  COMMIT,
  VIEWCHANGE,
  VIEWCHANGE_COMMIT,
  QUERY_EXEC
}
