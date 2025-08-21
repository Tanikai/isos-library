package isos.communication.client;

import isos.communication.ClientMessageWrapper;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is adapted from {@link bftsmart.tom.client.AbstractRequestHandler} and {@link
 * bftsmart.tom.client.NormalRequestHandler}. It is used solely on the client side of the consensus
 * algorithm.
 *
 * <p>It creates the request (which has to be sent separately with the communication system),
 * handles the timeout, processes the quorum of messages, and stores the quorum response.
 */
public class SingleRequestHandler<T> implements RequestReplyHandler<T> {
  protected final Logger logger;

  // Request Handling
  private final int ownClientId;
  private final long sequenceNumber;

  // Response Handling
  private final long responseTimeout;
  private final Set<ReplicaId> allowedReplicas;
  private final int quorumSize;
  private final Map<ReplicaId, ClientMessageWrapper> replies;

  private T quorumResponse;

  private final Comparator<ClientMessageWrapper> replyComparator;
  private final ReplyExtractor<T> replyExtractor;

  private final CompletableFuture<T> quorumReplyFuture;

  public SingleRequestHandler(
      int ownClientId,
      long sequenceNumber,
      long responseTimeoutSeconds,
      List<ReplicaId> replicas,
      int quorumSize,
      Comparator<ClientMessageWrapper> replyComparator,
      ReplyExtractor<T> replyExtractor) {
    this.logger =
        LoggerFactory.getLogger(
            String.format("RequestHandler ID %d SEQ %d", ownClientId, sequenceNumber));

    // Request Handling
    this.ownClientId = ownClientId;
    this.sequenceNumber = sequenceNumber;

    // Response Handling
    this.quorumReplyFuture =
        new CompletableFuture<T>().orTimeout(responseTimeoutSeconds, TimeUnit.SECONDS);
    this.responseTimeout = responseTimeoutSeconds;
    this.allowedReplicas = new HashSet<>(replicas);
    this.quorumSize = quorumSize;

    this.replyComparator = replyComparator;
    this.replyExtractor = replyExtractor;

    this.replies = new HashMap<>(replicas.size());
  }

  @Override
  public ClientMessageWrapper createRequest(byte[] payload) {
    return new ClientMessageWrapper(ownClientId, sequenceNumber, payload);
  }

  @Override
  public long getSequenceId() {
    return this.sequenceNumber;
  }

  @Override
  public void waitForResponse() throws InterruptedException {
    this.quorumReplyFuture.wait();
  }

  /**
   * Not thread safe.
   *
   * @param reply
   * @return
   */
  @Override
  public Optional<T> processReply(ClientMessageWrapper reply) throws QuorumNotReachedException {
    if (this.quorumResponse != null) {
      // response was already made, so we can throw away the reply and just use the already
      // calculated response
      return Optional.of(this.quorumResponse);
    }

    var senderId = new ReplicaId(reply.getSender());

    if (!this.allowedReplicas.contains(senderId)) {
      logger.info("Received replica from not allowed replica {}, throwing message away", senderId);
      return Optional.empty();
    }

    if (this.sequenceNumber != reply.getClientSequence()) {
      logger.info("Client sequence number does not match. Throwing message away");
      return Optional.empty();
    }

    if (this.replies.containsKey(senderId)) {
      logger.info("Already received response from {}", senderId);
      return Optional.empty();
    }

    // After all checks whether the response is valid, we can add it to our map
    this.replies.put(senderId, reply);

    if (replies.size() < this.quorumSize) {
      // We have less responses than the quorum -> no response yet
      return Optional.empty();
    }

    // We have a quorum number of replies -> we can now compare the messages and see if we actually
    // have a quorum or not
    var sameContent =
        replies.values().stream()
            .map(
                r -> {
                  if (this.replyComparator.compare(r, reply)
                      == 0) { // if replies are equal, the reply, else null
                    return r;
                  } else {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .toList();

    if (sameContent.size() >= quorumSize) {
      // we have reached quorum! we can set the response and return the value

      T finalReply = this.replyExtractor.extractReply(sameContent);
      this.quorumReplyFuture.complete(finalReply);
      return Optional.of(finalReply);
    }

    // We have not yet reached quorum, i.e., at least one of the messages differs from the other
    // messages. However, we have to check if we can even receive additional responses or not to
    // reach quorum.
    if (replies.size() == this.allowedReplicas.size()) {
      var ex =
          new QuorumNotReachedException(
              String.format(
                  "Received all %d replies, but no quorum could be reached.", replies.size()));
      this.quorumReplyFuture.completeExceptionally(ex);
      throw ex;
    }

    // We can still receive responses
    return Optional.empty();
  }

  @Override
  public int getNumberReceivedReplies() {
    return this.replies.size();
  }

  @Override
  public int getReplyQuorumSize() {
    return this.quorumSize;
  }

  @Override
  public CompletableFuture<T> getResponse() {
    return this.quorumReplyFuture;
  }
}
