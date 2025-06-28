package isos.communication.client;

import isos.communication.ClientMessageWrapper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This interface is used to handle the context and management of a single request-response process.
 * When an application uses this interface, it should be able to create multiple instances
 * implementing RequestHandler, in order to have multiple asynchronous, independent requests.
 * Received replies should be forwarded to processReply. ProcessReply should then compare whether
 * the contents of the received replies are the same or not.
 */
public interface RequestReplyHandler<T> {

  /**
   * Creates a request with the given payload.
   *
   * @return
   */
  ClientMessageWrapper createRequest(byte[] payload);

  int getSequenceId();

  void waitForResponse() throws InterruptedException;

  Optional<T> processReply(ClientMessageWrapper reply) throws QuorumNotReachedException;

  int getNumberReceivedReplies();

  int getReplyQuorumSize();

  CompletableFuture<T> getResponse();
}
