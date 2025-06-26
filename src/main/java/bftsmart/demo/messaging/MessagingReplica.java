package bftsmart.demo.messaging;

import bftsmart.communication.MessageHandler;
import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.message.TestMessage;
import bftsmart.tom.util.KeyLoader;
import isos.message.ClientMessageWrapper;
import java.util.concurrent.locks.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagingReplica extends Thread {

  // goal: send broadcast of some kind of message with signature of replica,
  // receivers can verify the signature

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int procId;

  private final ConfigurationManager configManager;

  // handles all communication with replicas and clients.
  private ServerCommunicationSystem scs;

  private Lock readyLock;

  public MessagingReplica(int procId, String configHome, KeyLoader loader) {
    super("Application ID " + procId);
    this.procId = procId;

    this.configManager = new ConfigurationManager(procId, configHome, loader);
    try {
      this.scs = new ServerCommunicationSystem(configManager, new TestMessageHandler());
      this.scs.setRequestReceiver(new ClientMessageHandler(scs));
    } catch (Exception e) {
      logger.error("Failed to create ServerCommunicationSystem", e);
    }
  }

  @Override
  public void run() {
    logger.debug("Messaging replica started");

    // start listening for messages
    this.scs.start();
    logger.info("Wait until view is connected");
    this.scs.waitUntilViewConnected();

    // wait until everyone is connected
    // send message to all replicas
    var receivers = this.configManager.getStaticConf().getInitialView();
    logger.info("Sending message to replicas {}", receivers);
    for (int i = 0; i < 100; i++) {
      // send a message to all replicas
      var msg = new TestMessage(procId, "Hello " + i + " from " + procId);
      this.scs.send(receivers, msg);
      // wait a bit
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        logger.error("Interrupted while waiting", e);
      }
    }
  }

  /**
   * Class that handles incoming messages from other replicas. Passed to the
   * ServerCommunicationSystem.
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

    private ServerCommunicationSystem scs;

    public ClientMessageHandler(ServerCommunicationSystem scs) {
      this.scs = scs;
    }

    @Override
    public void requestReceived(ClientMessageWrapper sm, boolean fromClient) {
      var payload = sm.getPayload();
      var sender = sm.getSender();
      var msg = new String(payload);
      logger.info("Received request from client {}, message: {}", sender, msg);
      logger.info("Sending reply");

      // TODO Kai: send reply
      var responsePayload = ("This is the reply").getBytes();
      var receivers = new int[1];
      receivers[0] = sender;
      this.scs.sendToClient(
          receivers,
          new ClientMessageWrapper(
              sm.getSender(), sm.getClientSession(), sm.getClientSequence(), responsePayload));
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
