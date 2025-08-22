package isos.benchmark.application;

import isos.api.ISOSClient;
import isos.benchmark.application.model.KVCommand;
import isos.benchmark.application.model.KVCommandType;
import isos.communication.client.QuorumNotReachedException;
import isos.message.client.OrderedClientReply;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class KVStoreClient {

  private ISOSClient client;

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Use: java KVStoreClient <processId>");
      System.exit(-1);
    }
    new KVStoreClient(Integer.parseInt(args[0]));
  }

  public KVStoreClient(int clientId) {
    this.client = new ISOSClient(clientId);
  }

  public String get(String key) throws IOException, TimeoutException, QuorumNotReachedException {
    KVCommand cmd = new KVCommand(KVCommandType.GET, key, "");

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(cmd);
      byte[] cmdBytes = bos.toByteArray();

      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(cmdBytes);
      return Arrays.toString(reply.response());
    }
  }

  public void put(String key, String data)
      throws IOException, TimeoutException, QuorumNotReachedException {
    KVCommand cmd = new KVCommand(KVCommandType.PUT, key, data);

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(cmd);
      byte[] cmdBytes = bos.toByteArray();

      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(cmdBytes);
      return;
    }
  }
}
