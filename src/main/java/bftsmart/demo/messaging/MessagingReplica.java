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
    super("Messaging Replica ID " + procId);
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
    this.serverComms.waitUntilViewConnected();


    // wait until everyone is connected
//    readyLock.


    // send message to all replicas
    var receivers = this.configManager.getStaticConf().getInitialView();
    logger.info("Sending message to replicas {}", receivers);
    for (int i = 0; i < 10; i++) {
      // send a message to all replicas
      var msg = new TestMessage(procId, "Hello " + i + " from " + procId);
      this.serverComms.send(receivers, msg);
      // wait a bit
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        logger.error("Interrupted while waiting", e);
      }
    }
  }

  class TestMessageHandler implements MessageHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void verifyPending() {}

    @Override
    public void processData(SystemMessage sm) {
      logger.info("Replica {} received message {}", procId, sm);
    }
  }

  class ClientMessageHandler implements RequestReceiver {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void requestReceived(TOMMessage msg, boolean fromClient) {
      logger.info("Received request from client {}", msg.getSender());
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
