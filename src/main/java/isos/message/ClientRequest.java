package isos.message;

public interface ClientRequest {
  int clientId();
  byte[] command();
  long clientLocalTimestamp();

  String calculateHash();
}
