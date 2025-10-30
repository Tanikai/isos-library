package isos.benchmark.ycsb;

import bftsmart.configuration.ConfigurationManager;
import bftsmart.demo.ycsb.YCSBMessage;
import bftsmart.demo.ycsb.YCSBTable;
import isos.api.ISOSApplication;
import isos.message.client.OrderedClientReply;
import isos.message.client.OrderedClientRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.TreeMap;

/**
 * YCSB server for ISOS benchmarks, adapted from {@link bftsmart.demo.ycsb.YCSBClient}.
 *
 * @author Marcel Santos
 * @author Kai Anter
 */
public class IsosYcsbServer {

  private final TreeMap<String, YCSBTable> mTables;
  private final ISOSApplication app;
  private final ConfigurationManager configManager;

  public static void main(String[] args) throws Exception {
    if (args.length == 1) {
      int clientId = Integer.parseInt(args[0]);
      System.out.println("Starting IsosYcsbServer with clientId " + clientId);
      var dbServer = new IsosYcsbServer(clientId);
      dbServer.start();
    } else {
      System.out.println("Usage: java isos.benchmark.ycsb.IsosYcsbServer <replica id>");
    }
  }

  public IsosYcsbServer(int replicaId) throws Exception {
    this.configManager = new ConfigurationManager(replicaId, "", null);
    this.mTables = new TreeMap<>();

    this.app =
        new ISOSApplication(
            this.configManager,
            this::deserializeYCSBMessage,
            this::conflict,
            this::executeClientRequest);
  }

  public void start() {
    this.app.start();
  }

  private YCSBMessage deserializeYCSBMessage(byte[] payload)
      throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      return (YCSBMessage) ois.readObject();
    }
  }

  private boolean conflict(OrderedClientRequest r1, OrderedClientRequest r2) {
    YCSBMessage cmd1 = r1.getDeserializedCommandCache();
    YCSBMessage cmd2 = r2.getDeserializedCommandCache();

    // If both commands are read, they do not conflict
    if (cmd1.getType().equals(YCSBMessage.Type.READ)
        && cmd2.getType().equals(YCSBMessage.Type.READ)) {
      return false;
    }

    if (cmd1.getTable().equals(cmd2.getTable()) && cmd1.getKey().equals(cmd2.getKey())) {
      // if Table and Key are same, and at least one is a write request, they conflict with each
      // other
      return true;
    }

    return false;
  }

  private void executeClientRequest(OrderedClientRequest r) {
    YCSBMessage command = r.getDeserializedCommandCache();
    YCSBMessage reply = YCSBMessage.newErrorMessage("Undefined response");
    String table = command.getTable();
    String key = command.getKey();
    YCSBMessage.Entity entity = command.getEntity();

    switch (command.getType()) {
      case READ -> {
        switch (entity) {
          case RECORD -> {
            if (!mTables.containsKey(table)) {
              reply = YCSBMessage.newErrorMessage(String.format("Table %s not found", table));
              break;
            }
            if (!mTables.get(table).containsKey(key)) {
              reply =
                  YCSBMessage.newErrorMessage(
                      String.format("Key %s in Table %s not found", key, table));
              break;
            }
            reply = YCSBMessage.newReadResponse(mTables.get(table).get(key), 0);
          }
        }
      }

      case CREATE -> {
        switch (entity) {
          case RECORD -> {
            if (!mTables.containsKey(table)) {
              mTables.put(table, new YCSBTable());
            }
            if (!mTables.get(table).containsKey(key)) {
              // Create key with values
              mTables.get(table).put(key, command.getValues());
              reply = YCSBMessage.newInsertResponse(0);
            }
          }
        }
      }

      case UPDATE -> {
        switch (entity) {
          case RECORD -> {
            if (!mTables.containsKey(table)) {
              mTables.put(table, new YCSBTable());
            }
            mTables.get(table).put(key, command.getValues());
            reply = YCSBMessage.newUpdateResponse(1);
          }
        }
      }
    }

    var replyMessage = new OrderedClientReply(reply.getBytes());
    this.app.sendClientReply(r, replyMessage);
  }
}
