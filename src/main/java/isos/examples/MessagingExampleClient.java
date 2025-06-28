package isos.examples;

import isos.api.ISOSClient;
import isos.communication.client.QuorumNotReachedException;
import isos.message.OrderedClientReply;
import isos.message.OrderedClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class MessagingExampleClient {
  private static final Logger logger = LoggerFactory.getLogger("MessagingExampleClient");

  public static void main(String[] args) throws IOException {
    int processId = 1;
    try (ISOSClient client = new ISOSClient(processId)) {
      client.setCurrentQuorumSize(3); // We want 3 responses before we return the result
      logger.info("Try sending message");

      byte[] msg = ("Hello from client!").getBytes();
      var r = new OrderedClientRequest(processId, msg, 123);
      try {
        var response = (OrderedClientReply) client.sendRequest(r);
        logger.info("Received response: {}", new String(response.response()));
      } catch (TimeoutException e)  {
        logger.error("Timeout reached for request");
      } catch (QuorumNotReachedException e) {
        logger.error("Quorum could not be reached for request");
      }
    }
  }
}
