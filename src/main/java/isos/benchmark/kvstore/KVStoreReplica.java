package isos.benchmark.kvstore;

import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.KeyLoader;
import isos.api.ISOSApplication;
import isos.benchmark.kvstore.model.KVMessage;
import isos.benchmark.kvstore.model.KVCommandType;
import isos.message.client.OrderedClientReply;
import isos.message.client.OrderedClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Server-side application for a simple KV-Store with GET and PUT operations, coordinated via ISOS.
 */
public class KVStoreReplica<K extends Serializable, V extends Serializable> {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ConfigurationManager configManager;
  private final ISOSApplication app;

  private final Map<K, V> kvState;

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Use: java isos.benchmark.kvstore.KVStoreReplica <processId>");
      System.exit(-1);
    }

    var replica = new KVStoreReplica<String, String>(Integer.parseInt(args[0]), "", null);
    replica.start();
  }

  public KVStoreReplica(int replicaId, String configHome, KeyLoader loader) throws Exception {
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
  @SuppressWarnings("unchecked")
  private KVMessage<K, V> deserializeCommand(byte[] commandBytes)
      throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(commandBytes);
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      return (KVMessage<K, V>) ois.readObject();
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
    KVMessage<K, V> cmd1 = r1.getDeserializedCommandCache();
    KVMessage<K, V> cmd2 = r2.getDeserializedCommandCache();

    // TODO Kai: conflict rules for keySet, size? Do they conflict with all requests?

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
   * @param r
   */
  private void executeClientRequest(OrderedClientRequest r) {
    KVMessage<K, V> cmd = r.getDeserializedCommandCache();

    OrderedClientReply response;
    try {
      switch (cmd.commandType()) {
        case GET -> {
          KVMessage<K, V> payload =
              new KVMessage<>(
                  KVCommandType.GET, cmd.key(), this.kvState.getOrDefault(cmd.key(), null));
          response = new OrderedClientReply(KVMessage.toBytes(payload));
        }
        case PUT -> {
          this.kvState.put(cmd.key(), cmd.data());

          KVMessage<K, V> payload =
              new KVMessage<>(
                  KVCommandType.GET, cmd.key(), this.kvState.getOrDefault(cmd.key(), null));
          response = new OrderedClientReply(KVMessage.toBytes(payload));
        }
        case GET_SIZE -> {
          KVMessage<K, V> payload = new KVMessage<>(KVCommandType.GET_SIZE, this.kvState.size());
          response = new OrderedClientReply(KVMessage.toBytes(payload));
        }
        case GET_KEYSET -> {
          KVMessage<K, V> payload =
              new KVMessage<>(KVCommandType.GET_KEYSET, new HashSet<>(this.kvState.keySet()));
          response = new OrderedClientReply(KVMessage.toBytes(payload));
        }
        default -> {
          logger.error("Unknown command type. Cannot execute client request");
          return;
        }
      }

      this.app.sendClientReply(r, response);
    } catch (Exception e) {
      logger.error("Exception while sending client reply: {}", e.getMessage());
    }
  }
}
