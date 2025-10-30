package bftsmart.communication.server;

@FunctionalInterface
public interface ByteArraySender {
  void send(byte[] data) ;
}
