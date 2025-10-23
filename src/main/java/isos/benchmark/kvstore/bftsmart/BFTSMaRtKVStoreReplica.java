package isos.benchmark.kvstore.bftsmart;

import bftsmart.demo.map.MapMessage;
import bftsmart.demo.map.MapRequestType;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import isos.benchmark.kvstore.model.KVCommandType;
import isos.benchmark.kvstore.model.KVMessage;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** See {@link bftsmart.demo.map.MapServer} */
public class BFTSMaRtKVStoreReplica<K extends Serializable, V extends Serializable>
    extends DefaultSingleRecoverable {

  private Map<K, V> replicaMap;
  private final Logger logger;

  public BFTSMaRtKVStoreReplica(int id) {
    replicaMap = new TreeMap<>();
    logger = Logger.getLogger(bftsmart.demo.map.MapServer.class.getName());
    new ServiceReplica(id, this, this);
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println(
          "Usage: isos.benchmark.kvstore.bftsmart.BFTSMaRtKVStoreReplica <server id>");
      System.exit(-1);
    }
    new BFTSMaRtKVStoreReplica<String, String>(Integer.parseInt(args[0]));
  }

  @Override
  public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
    try {
      KVMessage<K, V> request = KVMessage.fromBytes(command);
      KVMessage<K, V> response;

      switch (request.commandType()) {
        // write operations on the map
        case PUT:
          {
            replicaMap.put(request.key(), request.data());

            response =
                new KVMessage<>(KVCommandType.PUT, request.key(), replicaMap.get(request.key()));
          }
          break;
        case GET:
          {
            V ret = replicaMap.get(request.key());

            response = new KVMessage<>(KVCommandType.GET, request.key(), ret);
          }
          break;
        default:
          {
            response = new KVMessage<>(request.commandType(), request.key(), null);
          }
      }

      return KVMessage.toBytes(response);
    } catch (IOException | ClassNotFoundException ex) {
      logger.log(Level.SEVERE, "Failed to process ordered request", ex);
      return new byte[0];
    }
  }

  @Override
  public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
    try {
      MapMessage<K, V> response = new MapMessage<>();
      MapMessage<K, V> request = MapMessage.fromBytes(command);
      MapRequestType cmd = request.getType();

      switch (cmd) {
        // read operations on the map
        case GET:
          V ret = replicaMap.get(request.getKey());

          if (ret != null) {
            response.setValue(ret);
          }
          return MapMessage.toBytes(response);
        case SIZE:
          int size = replicaMap.size();
          response.setSize(size);
          return MapMessage.toBytes(response);
        case KEYSET:
          response.setKeySet(replicaMap.keySet());
          return MapMessage.toBytes(response);
      }
    } catch (IOException | ClassNotFoundException ex) {
      logger.log(Level.SEVERE, "Failed to process unordered request", ex);
      return new byte[0];
    }
    return new byte[0];
  }

  @Override
  public byte[] getSnapshot() {
    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutput objOut = new ObjectOutputStream(byteOut)) {
      objOut.writeObject(replicaMap);
      return byteOut.toByteArray();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error while taking snapshot", e);
    }
    return new byte[0];
  }

  @SuppressWarnings("unchecked")
  @Override
  public void installSnapshot(byte[] state) {
    try (ByteArrayInputStream byteIn = new ByteArrayInputStream(state);
        ObjectInput objIn = new ObjectInputStream(byteIn)) {
      replicaMap = (Map<K, V>) objIn.readObject();
    } catch (IOException | ClassNotFoundException e) {
      logger.log(Level.SEVERE, "Error while installing snapshot", e);
    }
  }
}
