package bftsmart.demo.messaging;

import bftsmart.communication.MessageHandler;
import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.message.TestMessage;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.KeyLoader;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;

import java.util.concurrent.locks.Lock;

public class MessagingReplica extends Thread {

  // goal: send broadcast of some kind of message with signature of replica,
  // receivers can verify the signature

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int procId;

  private final ConfigurationManager configManager;

  // handles all communication with replicas and clients.
  private ServerCommunicationSystem serverComms;

  private Lock readyLock;

  public MessagingReplica(int procId, String configHome, KeyLoader loader) {
    super("Application ID " + procId);
    this.procId = procId;

    this.configManager = new ConfigurationManager(procId, configHome, loader);
    try {
      this.serverComms = new ServerCommunicationSystem(configManager, new TestMessageHandler());
      this.serverComms.setRequestReceiver(new ClientMessageHandler());
    } catch (Exception e) {
      logger.error("Failed to create ServerCommunicationSystem", e);
    }
  }

  @Override
  public void run() {
    logger.debug("Messaging replica started");

    // start listening for messages
    this.serverComms.start();
    logger.info("Wait until view is connected");
    this.serverComms.waitUntilViewConnected();

    // wait until everyone is connected
    // send message to all replicas
    var receivers = this.configManager.getStaticConf().getInitialView();
    logger.info("Sending message to replicas {}", receivers);
    for (int i = 0; i < 100; i++) {
      // send a message to all replicas
      var msg = new TestMessage(procId, "Hello " + i + " from " + procId);
      this.serverComms.send(receivers, msg);
      // wait a bit
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        logger.error("Interrupted while waiting", e);
      }
    }
  }

  /**
   * Class that handles incoming messages from other replicas. Passed to the ServerCommunicationSystem.
   */
  class TestMessageHandler implements MessageHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void verifyPending() {}

    @Override
    public void processData(SystemMessage sm) {
      if (sm.getSender() == procId) {
        // ignore messages from self
        return;
      }
      logger.info("Replica {} received message {}", procId, sm);
    }
  }

  class ClientMessageHandler implements RequestReceiver {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void requestReceived(TOMMessage msg, boolean fromClient) {
      logger.info("Received request from client {}, message: {}", msg.getSender(), new String(msg.serializedMessage));

      // TODO Kai: send reply
    }
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Use: java MessagingReplica <processId>");
      System.exit(-1);
    }
    new MessagingReplica(Integer.parseInt(args[0]), "", null).start();
  }
}
