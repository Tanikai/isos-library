package isos.benchmark.kvstore;

import isos.api.ISOSClient;
import isos.benchmark.kvstore.model.KVCommandType;
import isos.benchmark.kvstore.model.KVMessage;
import isos.communication.client.QuorumNotReachedException;
import isos.message.client.OrderedClientReply;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;

public class KVStoreClient<K extends Serializable, V extends Serializable> {

  private final ISOSClient client;

  public KVStoreClient(int clientId) {
    this.client = new ISOSClient(clientId);
  }

  public V get(K key)
      throws IOException, TimeoutException, QuorumNotReachedException, ClassNotFoundException {
    KVMessage<K, V> cmd = new KVMessage<>(KVCommandType.GET, key, null);

    OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(KVMessage.toBytes(cmd));
    KVMessage<K, V> payload = KVMessage.fromBytes(reply.response());
    return payload.data();
  }

  public void put(K key, V data)
      throws IOException, TimeoutException, QuorumNotReachedException, ClassNotFoundException {
    KVMessage<K, V> cmd = new KVMessage<>(KVCommandType.PUT, key, data);

    OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(KVMessage.toBytes(cmd));
    KVMessage<K, V> payload = KVMessage.fromBytes(reply.response());
  }

  public int getSize()
      throws IOException, TimeoutException, QuorumNotReachedException, ClassNotFoundException {
    KVMessage<K, V> cmd = new KVMessage<>(KVCommandType.GET_SIZE, null, null);
    OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(KVMessage.toBytes(cmd));
    KVMessage<K, V> payload = KVMessage.fromBytes(reply.response());
    return payload.size();
  }

  public HashSet<K> getKeySet()
      throws IOException, TimeoutException, QuorumNotReachedException, ClassNotFoundException {
    KVMessage<K, V> cmd = new KVMessage<>(KVCommandType.GET_KEYSET, null, null);
    OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(KVMessage.toBytes(cmd));
    KVMessage<K, V> payload = KVMessage.fromBytes(reply.response());
    return payload.keySet();
  }

  public void close() {
    this.client.close();
  }
}
