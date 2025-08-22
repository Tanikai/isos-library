package isos.benchmark.application;

import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.KeyLoader;
import isos.api.ISOSApplication;
import isos.benchmark.application.model.KVCommand;
import isos.benchmark.application.model.KVCommandType;
import isos.message.client.OrderedClientReply;
import isos.message.client.OrderedClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side application for a simple KV-Store with GET and PUT operations, coordinated via ISOS.
 */
public class KVStoreReplica {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConfigurationManager configManager;
  private final ISOSApplication app;

  private final Map<String, String> kvState;

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Use: java KVStoreReplica <processId>");
      System.exit(-1);
    }
    new KVStoreReplica(Integer.parseInt(args[0]), "", null).start();
  }

  public KVStoreReplica(int replicaId, String configHome, KeyLoader loader) {
    this.configManager = new ConfigurationManager(replicaId, configHome, loader);
    this.app =
        new ISOSApplication(
            configManager,
            this::deserializeCommand,
            this::doesCommandConflict,
            this::executeClientRequest);
    this.kvState = new HashMap<>();
  }

  public void start() {
    this.app.start();
  }

  /**
   * @param commandBytes
   * @return
   * @throws IOException
   * @throws ClassNotFoundException
   */
  private KVCommand deserializeCommand(byte[] commandBytes)
      throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(commandBytes);
        ObjectInputStream ois = new ObjectInputStream(bis)) {

      return (KVCommand) ois.readObject();
    }
  }

  /**
   * Conflict predicate for the KV store.
   *
   * @param r1
   * @param r2
   * @return
   */
  private boolean doesCommandConflict(OrderedClientRequest r1, OrderedClientRequest r2) {
    KVCommand cmd1 = r1.getDeserializedCommandCache();
    KVCommand cmd2 = r2.getDeserializedCommandCache();

    // If two commands access different keys, they do not conflict
    if (!cmd1.key().equals(cmd2.key())) {
      return false;
    }

    // Same key -> now we have to check whether read / write
    // If both commands are GET (read), they do not conflict with each other
    if (cmd1.commandType().equals(KVCommandType.GET)
        && cmd2.commandType().equals(KVCommandType.GET)) {
      return false;
    }

    // Else, if both of them are write, they conflict each other (different results based on
    // execution order)
    // If one of them is write and the other is read, they conflict as well (reads should return the
    // current value of the write)
    return true;
  }

  /**
   *
   * @param r
   */
  private void executeClientRequest(OrderedClientRequest r) {
    KVCommand cmd = r.getDeserializedCommandCache();

    OrderedClientReply response;

    switch (cmd.commandType()) {
      case GET -> {
        var data = this.kvState.getOrDefault(cmd.key(), "");
        response = new OrderedClientReply(data.getBytes());
      }
      case PUT -> {
        this.kvState.put(cmd.key(), cmd.data());
        response = new OrderedClientReply("ok".getBytes());
      }
      default -> {
        logger.error("Unknown command type. Cannot execute client request");
        return;
      }
    }

    this.app.sendClientReply(r, response);
  }
}
