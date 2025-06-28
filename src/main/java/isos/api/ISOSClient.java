package isos.api;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.KeyLoader;
import isos.communication.*;
import isos.communication.client.QuorumNotReachedException;
import isos.communication.client.ReplyExtractor;
import isos.communication.client.RequestReplyHandler;
import isos.communication.client.SingleRequestHandler;
import isos.message.ClientReply;
import isos.message.ClientRequest;
import isos.utils.QuorumUtil;
import isos.utils.ReplicaId;
import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is the ISOS equivalent of the {@link bftsmart.tom.core.TOMSender}. It */
public class ISOSClient implements ReplyReceiver, Closeable, AutoCloseable {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final CommunicationSystemClientSide ccs;
  private final ConfigurationManager configManager;
  private final int ownClientId;
  private final boolean useSignatures; // Should we sign our requests or not?
  private final int
      session; // session id, randomly generated each time a new isos client is created
  private final AtomicInteger requestSequenceCounter = new AtomicInteger(0);

  private long requestTimeoutSeconds = 40; // 40 seconds

  private RequestReplyHandler<ClientReply> currentRequestContext;

  private int currentF;
  private int currentQuorumSize;

  private List<ReplicaId> currentOverallView;


  public ISOSClient(int processId) {
    this(processId, null, null);
  }

  public ISOSClient(int processId, String configHome, KeyLoader loader) {
    if (configHome == null) {
      this.configManager = new ConfigurationManager(processId, loader);
    } else {
      this.configManager = new ConfigurationManager(processId, configHome, loader);
    }
    this.ownClientId = this.configManager.getStaticConf().getProcessId();
    this.ccs =
        CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(
            processId, this.configManager);
    this.ccs.setReplyReceiver(this); // This object itself shall be a reply receiver
    this.useSignatures = this.configManager.getStaticConf().getUseSignatures() == 1;
    this.session = new Random().nextInt();
    this.currentOverallView =
        Arrays.stream(this.configManager.getCurrentViewIds())
            .mapToObj(ReplicaId::new)
            .collect(Collectors.toList());

    this.currentF = this.configManager.getStaticConf().getF();
    this.currentQuorumSize =
        QuorumUtil.getReplyQuorum(this.currentOverallView.size(), this.currentF, true);
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
   * @param r Request that is sent as the payload.
   * @return The reply that is confirmed by a quourm of
   */
  public ClientReply sendRequest(ClientRequest r)
      throws IOException, TimeoutException, QuorumNotReachedException {
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
        new SingleRequestHandler<ClientReply>(
            ownClientId,
            this.session,
            this.requestSequenceCounter.getAndIncrement(),
            this.requestTimeoutSeconds,
            this.currentOverallView,
            this.currentQuorumSize,
            comparator,
            extractor);

    byte[] payload;
    // create request payload with application-specific data
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(r);
      out.flush();
      payload = bos.toByteArray();
    } catch (IOException e) {
      logger.error("");
      // rethrow exception
      throw e;
    }

    // create request wrapper containing metadata
    ClientMessageWrapper request = this.currentRequestContext.createRequest(payload);

    // multicast request
    this.ccs.send(this.useSignatures, this.currentOverallView, request, this.currentQuorumSize);

    // wait for future
    var replyFuture = this.currentRequestContext.getResponse();
    ClientReply response = null;
    try {
      while (!replyFuture.isDone()) {
        // we have to wait in a loop, due to InterruptedException
        response = replyFuture.get();
        // when we are here, the future was completed.
      }

      return response;

    } catch (InterruptedException e) {
      // While we were waiting for the response with .get(), we were interrupted.
      // In the future, we might need to check whether to abort the whole ISOSClient or not.
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
