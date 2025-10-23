package isos.benchmark.kvstore.bftsmart;

import bftsmart.tom.ServiceProxy;
import isos.benchmark.kvstore.model.KVCommandType;
import isos.benchmark.kvstore.model.KVMessage;

import java.io.IOException;
import java.io.Serializable;

public class BFTSMaRtKVStoreClient<K extends Serializable, V extends Serializable> {

  ServiceProxy serviceProxy;

  public BFTSMaRtKVStoreClient(int clientId) {
    serviceProxy = new ServiceProxy(clientId);
  }

  public V put(K key, V value) {
    byte[] rep;
    try {
      var request = new KVMessage<>(KVCommandType.PUT, key, value);

      // invokes BFT-SMaRt
      rep = serviceProxy.invokeOrdered(KVMessage.toBytes(request));
    } catch (IOException e) {
      System.out.println("Failed to send PUT request: " + e.getMessage());
      return null;
    }
    if (rep.length == 0) {
      return null;
    }

    try {
      KVMessage<K, V> response = KVMessage.fromBytes(rep);
      return response.data();
    } catch (ClassNotFoundException | IOException ex) {
      System.out.println("Failed to deserialized response of PUT request: " + ex.getMessage());
      return null;
    }
  }

  public V get(Object key) {
    byte[] rep;
    try {
      var request = new KVMessage<>(KVCommandType.GET, null);

      // invokes BFT-SMaRt
      rep = serviceProxy.invokeUnordered(KVMessage.toBytes(request));
    } catch (IOException e) {
      System.out.println("Failed to send GET request: " + e.getMessage());
      return null;
    }

    if (rep.length == 0) {
      return null;
    }
    try {
      KVMessage<K, V> response = KVMessage.fromBytes(rep);
      return response.data();
    } catch (ClassNotFoundException | IOException ex) {
      System.out.println("Failed to deserialized response of GET request: " + ex.getMessage());
      return null;
    }
  }

  public void close() {
    serviceProxy.close();
  }
}
