package isos.benchmark.ycsb;

import bftsmart.demo.ycsb.YCSBMessage;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.Status;
import isos.api.ISOSClient;
import isos.message.client.OrderedClientReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for the Yahoo Cloud Serving Benchmark. From the DB.java comment:
 *
 * <p>A layer for accessing a database to be benchmarked. Each thread in the client will be given
 * its own instance of whatever DB class is to be used in the test. This class should be constructed
 * using a no-argument constructor, so we can load it dynamically. Any argument-based initialization
 * should be done by init().
 *
 * <p>Note that YCSB does not make any use of the return codes returned by this class. Instead, it
 * keeps a count of the return values and presents them to the user.
 *
 * <p>The semantics of methods such as insert, update and delete vary from database to database. In
 * particular, operations may or may not be durable once these methods commit, and some systems may
 * return 'success' regardless of whether or not a tuple with a matching key existed before the
 * call. Rather than dictate the exact semantics of these methods, we recommend you either implement
 * them to match the database's default semantics, or the semantics of your target application. For
 * the sake of comparison between experiments we also recommend you explain the semantics you chose
 * when presenting performance results.
 *
 * @author Marcel Santos
 * @author Kai Anter
 */
public class IsosYcsbClient extends DB {
  private static final AtomicLong requestCounter = new AtomicLong(0);

  private final int REQUEST_LOG_INTERVAL = 50;

  private Logger logger;
  private static AtomicInteger counter = new AtomicInteger();
  private int ownClientId = -1;
  private ISOSClient client;

  public IsosYcsbClient() {}

  /**
   * https://github.com/brianfrankcooper/YCSB/blob/master/core/src/main/java/site/ycsb/DB.java
   * Called once per DB instance; there is one DB instance per client thread.
   */
  @Override
  public void init() {
    Properties props = getProperties();
    int initId = Integer.parseInt((String) props.get("smart-initkey"));
    if (this.ownClientId != -1) {
      throw new RuntimeException("double initialized");
    }
    this.ownClientId = initId + counter.addAndGet(1);
    this.logger = LoggerFactory.getLogger(String.format("IsosYcsbClient %d", this.ownClientId));

    this.client = new ISOSClient(this.ownClientId);
  }

  /** Called once per DB instance; there is one DB instance per client thread. */
  @Override
  public void cleanup() {}

  @Override
  public Status delete(String arg0, String arg1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
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
      var completedRequests = IsosYcsbClient.counter.addAndGet(1);
      if (completedRequests % REQUEST_LOG_INTERVAL == 0) {
        logger.info("INSERT: Received reply, total completed requests: {}", completedRequests);
      }

      if (replyMsg.getErrorMsg() != null && !replyMsg.getErrorMsg().isEmpty()) {
        logger.error("INSERT: Error message {}", replyMsg.getErrorMsg());
        return Status.ERROR;
      }

      if (replyMsg.getResult() != 0) {
        logger.error("Received unsuccessful result value {}, reason unknown", replyMsg.getResult());
        return Status.ERROR;
      }

      return Status.OK;
    } catch (Exception e) {
      logger.error("INSERT: Exception {}", e.getMessage());
      return Status.ERROR;
    }
  }

  @Override
  public Status read(
      String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    HashMap<String, byte[]> results = new HashMap<>();

    try {
      YCSBMessage request = YCSBMessage.newReadRequest(table, key, fields, results);
      OrderedClientReply reply = (OrderedClientReply) this.client.sendRequest(request.getBytes());
      YCSBMessage replyMsg = YCSBMessage.getObject(reply.response());
      assert replyMsg != null;
      var completedRequests = IsosYcsbClient.counter.addAndGet(1);
      if (completedRequests % REQUEST_LOG_INTERVAL == 0) {
        logger.info("READ: Received reply, total completed requests: {}", completedRequests);
      }

      if (replyMsg.getErrorMsg() != null && !replyMsg.getErrorMsg().isEmpty()) {
        logger.error("READ: Error message {}", replyMsg.getErrorMsg());
        return Status.ERROR;
      }

      if (replyMsg.getResult() != 0) {
        logger.error("Received unsuccessful result value {}, reason unknown", replyMsg.getResult());
        return Status.ERROR;
      }

      return Status.OK;
    } catch (Exception e) {
      logger.error("READ: Exception {}", e.getMessage());
      return Status.ERROR;
    }
  }

  @Override
  public Status scan(
      String arg0,
      String arg1,
      int arg2,
      Set<String> arg3,
      Vector<HashMap<String, ByteIterator>> arg4) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
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
      var completedRequests = IsosYcsbClient.counter.addAndGet(1);
      if (completedRequests % REQUEST_LOG_INTERVAL == 0) {
        logger.info("UPDATE: Received reply, total completed requests: {}", completedRequests);
      }

      if (replyMsg.getErrorMsg() != null && !replyMsg.getErrorMsg().isEmpty()) {
        logger.error("UPDATE: Error message {}", replyMsg.getErrorMsg());
        return Status.ERROR;
      }

      if (replyMsg.getResult() != 0) {
        logger.error("Received unsuccessful result value {}, reason unknown", replyMsg.getResult());
        return Status.ERROR;
      }

      return Status.OK;
    } catch (Exception e) {
      logger.error("UPDATE: Exception {}", e.getMessage());
      return Status.ERROR;
    }
  }
}
