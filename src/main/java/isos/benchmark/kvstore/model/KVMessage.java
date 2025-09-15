package isos.benchmark.kvstore.model;

import java.io.*;
import java.util.HashSet;

public record KVMessage<K extends Serializable, V extends Serializable>(
    KVCommandType commandType, K key, V data, HashSet<K> keySet, int size) implements Serializable {

  public KVMessage(KVCommandType commandType, K key, V data) {
    this(commandType, key, data, null, -1);
  }

  public KVMessage(KVCommandType commandType, HashSet<K> keySet) {
    this(commandType, null, null, keySet, -1);
  }

  public KVMessage(KVCommandType commandType, int size) {
    this(commandType, null, null, null, size);
  }

  public static <K extends Serializable, V extends Serializable> byte[] toBytes(KVMessage<K, V> obj)
      throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(obj);
      return bos.toByteArray();
    }
  }

  @SuppressWarnings("unchecked")
  public static <K extends Serializable, V extends Serializable> KVMessage<K, V> fromBytes(
      byte[] data) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis)) {
      return (KVMessage<K, V>) in.readObject();
    }
  }
}
