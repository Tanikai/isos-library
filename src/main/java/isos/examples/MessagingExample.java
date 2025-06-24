package isos.examples;

import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.KeyLoader;
import isos.api.ISOSApplication;
import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;
import isos.message.ISOSMessageWrapper;
import isos.message.fast.DepProposeMessage;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    var scs = this.app.debug_getSCS();
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
}
