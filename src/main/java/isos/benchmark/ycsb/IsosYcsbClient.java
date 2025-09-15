package isos.benchmark.ycsb;

import bftsmart.demo.ycsb.YCSBMessage;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import isos.api.ISOSClient;
import isos.message.client.OrderedClientReply;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client
 *
 * @author Marcel Santos
 * @author Kai Anter
 */
public class IsosYcsbClient extends DB {

  private static AtomicInteger counter = new AtomicInteger();
  private int ownClientId;
  private ISOSClient client;

  public IsosYcsbClient() {}

  @Override
  public void init() {
    Properties props = getProperties();
    int initId = Integer.valueOf((String) props.get("smart-initkey"));
    this.ownClientId = initId + counter.addAndGet(1);
    this.client = new ISOSClient(this.ownClientId);
    System.out.println("YCSBKVClient. Initiated client id: " + this.ownClientId);
  }

  @Override
  public int delete(String arg0, String arg1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int insert(String table, String key, HashMap<String, ByteIterator> values) {
    Iterator<String> keys = values.keySet().iterator();
    HashMap<String, byte[]> map = new HashMap<>();
    while (keys.hasNext()) {
      String field = keys.next();
      map.put(field, values.get(field).toArray());
    }

    try {
      YCSBMessage insertCmd = YCSBMessage.newInsertRequest(table, key, map);
      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(insertCmd.getBytes());
      YCSBMessage replyMsg = YCSBMessage.getObject(reply.response());
      assert replyMsg != null;
      return replyMsg.getResult();
    } catch (Exception e) {
      System.err.println(e);
      return -1;
    }
  }

  @Override
  public int read(
      String table, String key, Set<String> fields, HashMap<String, ByteIterator> result) {
    HashMap<String, byte[]> results = new HashMap<>();

    try {
      YCSBMessage request = YCSBMessage.newReadRequest(table, key, fields, results);
      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(request.getBytes());
      YCSBMessage replyMsg = YCSBMessage.getObject(reply.response());
      assert replyMsg != null;
      return replyMsg.getResult();
    } catch (Exception e) {
      // TODO Kai: What should we do in YCSB on error?
      return -1;
    }
  }

  @Override
  public int scan(
      String arg0,
      String arg1,
      int arg2,
      Set<String> arg3,
      Vector<HashMap<String, ByteIterator>> arg4) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(String table, String key, HashMap<String, ByteIterator> values) {
    Iterator<String> keys = values.keySet().iterator();
    HashMap<String, byte[]> map = new HashMap<>();
    while (keys.hasNext()) {
      String field = keys.next();
      map.put(field, values.get(field).toArray());
    }
    try {
      YCSBMessage msg = YCSBMessage.newUpdateRequest(table, key, map);
      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(msg.getBytes());
      YCSBMessage replyMsg = YCSBMessage.getObject(reply.response());
      assert replyMsg != null;
      return replyMsg.getResult();
    } catch (Exception e) {
      // TODO Kai:
      return -1;
    }
  }
}
