package isos.consensus;

/**
 * Exception thrown when a SequenceNumber with an invalid ReplicaId is passed to an AgreementSlotSequence.
 */
public class InvalidReplicaIdException extends RuntimeException {
  public InvalidReplicaIdException(String message) {
    super(message);
  }

  public InvalidReplicaIdException(String message, Throwable cause) {
    super(message, cause);
  }
}
