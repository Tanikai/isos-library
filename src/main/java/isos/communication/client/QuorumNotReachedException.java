package isos.communication.client;

public class QuorumNotReachedException extends Exception {
  public QuorumNotReachedException() {
    super();
  }

  public QuorumNotReachedException(String message) {
    super(message);
  }

  public QuorumNotReachedException(String message, Throwable cause) {
    super(message, cause);
  }

  public QuorumNotReachedException(Throwable cause) {
    super(cause);
  }
}
