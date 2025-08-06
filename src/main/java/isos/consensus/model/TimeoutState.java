package isos.consensus.model;

public enum TimeoutState {
  NULL,
  STARTED, // After a timeout has been created and started
  EXPIRED , // When a timeout expired after x seconds
  CANCELED, // When a timeout was canceled before it expired
}
