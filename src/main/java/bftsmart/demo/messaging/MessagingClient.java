package bftsmart.demo.messaging;

import bftsmart.tom.ServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class MessagingClient {

  private final static Logger logger = LoggerFactory.getLogger("MessagingClient");

  public static void main(String[] args) throws IOException {
    logger.info("Start client");

    int processId = 1;
    int numberOps = 10;
    try (ServiceProxy proxy = new ServiceProxy(processId)) {
      for (int i = 0; i < numberOps; i++) {
        logger.info("Try sending message {}", i);

        byte[] msg = ("Hello from client " + i).getBytes();
        // FIXME: currently there is no response from
        byte[] reply = proxy.invokeOrdered(msg);

        if (reply != null) {
          String response = new String(reply);
          logger.info("Received response: " + response);
        } else {
          logger.info("Received no response.");
        }
      }
    }
  }
}
