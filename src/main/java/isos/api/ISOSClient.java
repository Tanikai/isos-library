package isos.api;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.KeyLoader;
import isos.communication.ClientMessageWrapper;
import isos.communication.client.QuorumNotReachedException;
import isos.communication.client.ReplyExtractor;
import isos.communication.client.RequestReplyHandler;
import isos.communication.client.SingleRequestHandler;
import isos.message.client.ClientReply;
import isos.message.client.OrderedClientRequest;
import isos.utils.QuorumUtil;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** This is the ISOS equivalent of the {@link bftsmart.tom.core.TOMSender}. It */
public class ISOSClient implements ReplyReceiver, Closeable, AutoCloseable {
  private final Logger logger;

  private final CommunicationSystemClientSide ccs;
  private final ConfigurationManager configManager;
  private final int ownClientId;
  private final boolean useSignatures; // Should we sign our requests or not?

  private RequestReplyHandler<ClientReply> currentRequestContext;

  /** Store previously received responses. */
  private final ConcurrentHashMap<Long, ClientReply> completedSequenceNumbers;

  private int currentF;
  private int currentQuorumSize;

  // FIXME: Currently no support for replacing / adding / removing replicas
  private List<ReplicaId> currentOverallView;

  public ISOSClient(int processId) {
    this(processId, null, null);
  }

  public ISOSClient(int processId, String configHome, KeyLoader loader) {
    this.ownClientId = processId;
    this.logger =
        LoggerFactory.getLogger(String.format("%s-%d", this.getClass(), this.ownClientId));
    if (configHome == null) {
      this.configManager = new ConfigurationManager(ownClientId, loader);
    } else {
      this.configManager = new ConfigurationManager(ownClientId, configHome, loader);
    }
    this.ccs =
        CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(
            ownClientId, this.configManager);
    this.ccs.setReplyReceiver(this); // This object itself shall be a reply receiver
    this.useSignatures = this.configManager.getStaticConf().getUseSignatures() == 1;
    this.currentOverallView =
        Arrays.stream(this.configManager.getCurrentViewIds())
            .mapToObj(ReplicaId::new)
            .collect(Collectors.toList());

    this.currentF = this.configManager.getStaticConf().getF();
    this.currentQuorumSize =
        QuorumUtil.getReplyQuorum(this.currentOverallView.size(), this.currentF, true);

    this.completedSequenceNumbers = new ConcurrentHashMap<>();

    this.ccs.setPingTargets(this.currentOverallView);
  }

  public void close() {
    this.ccs.close();
  }

  public void setCurrentQuorumSize(int newQuorumSize) {
    this.currentQuorumSize = newQuorumSize;
  }

  /**
   * This is the equivalent of {@link bftsmart.tom.ServiceProxy#invoke(byte[], TOMMessageType)}. Not
   * thread-safe. Blocks until the result is received.
   *
   * <p>Uses the clientLocalTimestamp of the client request for assigning the responses from
   * replicas to this request.
   *
   * @param requestPayload Payload that is sent to the replica
   * @return The reply that is confirmed by a quorum of responses
   */
  public ClientReply sendRequest(byte[] requestPayload)
      throws IOException, TimeoutException, QuorumNotReachedException {

    if (this.currentRequestContext != null) {
      // For debugging
      throw new RuntimeException(
          "Tried to send new request before the current request is handled. This is currently not supported");
    }

    long clientLocalTimestamp = System.nanoTime(); // monotonic clock
    var request = new OrderedClientRequest(this.ownClientId, requestPayload, clientLocalTimestamp);

    Comparator<ClientMessageWrapper> comparator =
        (o1, o2) -> Arrays.equals(o1.getPayload(), o2.getPayload()) ? 0 : -1;
    ReplyExtractor<ClientReply> extractor =
        (replies) -> {
          var payload = replies.getFirst().getPayload();
          try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
              ObjectInputStream ois = new ObjectInputStream(bis)) {
            // return the ClientRequest that was serialized
            return (ClientReply) ois.readObject();
          } catch (IOException e) {
            logger.error("IOException: {}", e.getMessage());
            return null;
          } catch (ClassNotFoundException e) {
            logger.error("Could not find class while decoding reply: {}", e.getMessage());
            return null;
          }
        };

    this.currentRequestContext =
        new SingleRequestHandler<>(
            ownClientId,
            request.clientLocalTimestamp(),
            this.configManager.getStaticConf().getClientInvokeOrderedTimeout(), // 40s by default
            this.currentOverallView,
            this.currentQuorumSize,
            comparator,
            extractor);

    byte[] payload;
    // create request payload with application-specific data
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(request);
      out.flush();
      payload = bos.toByteArray();
    } catch (IOException e) {
      logger.error("");
      // rethrow exception
      throw e;
    }

    // create request wrapper containing metadata
    ClientMessageWrapper requestWrapper = this.currentRequestContext.createRequest(payload);

    // Send the request to the replica with lowest known latency
    ReplicaId lowestReplica =
        this.ccs.getCurrentPings().entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElseGet(
                () -> {
                  Random rand = new Random();
                  return this.currentOverallView.get(rand.nextInt(this.currentOverallView.size()));
                });
    this.ccs.send(
        this.useSignatures, List.of(lowestReplica), requestWrapper, this.currentQuorumSize);

    // wait for future
    var replyFuture = this.currentRequestContext.getResponse();
    ClientReply response = null;
    try {
      while (!replyFuture.isDone()) {
        // we have to wait in a loop, due to InterruptedException
        response = replyFuture.get();
        // when we are here, the future was completed.
      }
      if (response != null) {
        this.completedSequenceNumbers.put(this.currentRequestContext.getSequenceId(), response);
      } else {
        logger.warn("Quorum of responses is null. This might not be intended.");
      }
      this.currentRequestContext = null;
      return response;

    } catch (InterruptedException e) {
      // While we were waiting for the response with .get(), we were interrupted.
      // In the future, we might need to check whether to abort the whole ISOSClient or not.
      logger.error("Interrupted while waiting for request.");
    } catch (ExecutionException e) {
      // When we throw an exception with .completeExceptionally(Throwable cause), we will get here.
      Throwable cause = e.getCause();
      if (cause instanceof QuorumNotReachedException exNotReached) {
        logger.error(
            "We have not reached enough identical replies to form a quorum. The request has to be repeated.");
        throw exNotReached;
      } else if (cause instanceof TimeoutException exTimeout) {
        logger.error("Timeout reached for request. The request has to be repeated.");
        // rethrow exception
        throw exTimeout;
      } else {
        logger.error("Unknown Exception thrown by Future: {}", cause.getMessage());
      }
    } catch (Exception e) {
      logger.error("Generic error while handling the request: {}", e.getMessage());
    }
    //
    return null;
  }

  @Override
  public void replyReceived(ClientMessageWrapper reply) {
    if (this.completedSequenceNumbers.containsKey(reply.getClientSequence())) {
      logger.debug(
          "Request with sequence number {} already completed with quorum. Ignore received replica reply.",
          reply.getClientSequence());
      return;
    }

    try {
      this.currentRequestContext.processReply(reply);
    } catch (QuorumNotReachedException e) {
      // quorum is impossible to reach. Clean up context and return error
      logger.error(e.getMessage());
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }
}
