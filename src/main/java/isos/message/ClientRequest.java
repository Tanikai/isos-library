package isos.message;

public interface ClientRequest {
  int clientId();

  byte[] command();

  long clientLocalTimestamp();

  String calculateHash();

  boolean equals(Object o);

  int hashCode();
}
