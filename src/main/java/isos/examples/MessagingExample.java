package isos.examples;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.KeyLoader;
import isos.api.ISOSApplication;
import isos.communication.ClientMessageWrapper;
import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessageWrapper;
import isos.message.OrderedClientReply;
import isos.message.OrderedClientRequest;
import isos.message.fast.DepProposeMessage;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;

/**
 * This class is just a test to see whether ISOSApplication can send and receive messages with other
 * replicas.
 */
public class MessagingExample extends Thread {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Use: java MessagingExample <processId>");
      System.exit(-1);
    }
    new MessagingExample(Integer.parseInt(args[0]), "", null).start();
  }

  private int replicaId;
  private ConfigurationManager configManager;

  private ISOSApplication app;

  public MessagingExample(int replicaId, String configHome, KeyLoader loader) {
    super(String.format("ReplicaId %d", replicaId));

    this.replicaId = replicaId;
    this.configManager = new ConfigurationManager(replicaId, configHome, loader);
    this.app = new ISOSApplication(configManager);
  }

  @Override
  public void run() {
    this.app.start();

    // Initialization
    var scs = this.app.debug_getSCS();
    var requestHandler = new ClientRequestHandler(this.replicaId, scs);
    scs.setRequestReceiver(requestHandler);

    var receivers = this.configManager.getStaticConf().getInitialView();
    var agreementSlotCount = 5;

    // Broadcast DepPropose message for own replica
    var seqNum = new SequenceNumber(replicaId, 0);
    var payload =
        new DepProposeMessage(
            seqNum, new ReplicaId(replicaId), "abc", new DependencySet(), new HashSet<>());
    var msg = new ISOSMessageWrapper(payload, replicaId);
    if (replicaId == 0) {
      logger.info("Broadcast message to other replicas");
      scs.broadcastToReplicas(true, msg);
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting", e);
    }
  }

  class ClientRequestHandler implements RequestReceiver {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private int ownReplicaId;
    private ServerCommunicationSystem scs;

    public ClientRequestHandler(int ownReplicaId, ServerCommunicationSystem scs) {
      this.ownReplicaId = ownReplicaId;
      this.scs = scs;
    }

    @Override
    public void requestReceived(ClientMessageWrapper msg, boolean fromClient) {
      var payload = msg.getPayload();
      try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
          ObjectInputStream ois = new ObjectInputStream(bis)) {
        OrderedClientRequest r = (OrderedClientRequest) ois.readObject();

        logger.info("Received OrderedClientRequest from client: {}", r);
      } catch (IOException | ClassNotFoundException e) {
        logger.warn("Failed to deserialize OrderedClientRequest from client payload", e);
      }

      var responseMessage = "Hello back from replica!";
      var responseObj = new OrderedClientReply(responseMessage.getBytes());
      byte[] responseBytes = null;
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(responseObj);
        oos.flush();
        responseBytes = bos.toByteArray();
      } catch (IOException e) {
        logger.warn("Failed to serialize OrderedClientReply for client response", e);
        return;
      }

      ClientMessageWrapper response =
          new ClientMessageWrapper(
              this.ownReplicaId, msg.getClientSession(), msg.getClientSequence(), responseBytes);

      int[] receivers = new int[1];
      receivers[0] = msg.getSender();
      this.scs.sendToClients(receivers, response);
    }
  }
}
