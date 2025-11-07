/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated
 * in the @author tags
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.demo.ycsb;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import bftsmart.tom.ServiceProxy;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.Status;

/**
 * @author Marcel Santos
 */
public class YCSBClient extends DB {

  private static AtomicInteger counter = new AtomicInteger();
  private ServiceProxy proxy = null;
  private int myId;

  public YCSBClient() {}

  @Override
  public void init() {
    Properties props = getProperties();
    int initId = Integer.valueOf((String) props.get("smart-initkey"));
    myId = initId + counter.addAndGet(1);
    proxy = new ServiceProxy(myId);
  }

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
    YCSBMessage msg = YCSBMessage.newInsertRequest(table, key, map);
    byte[] reply = proxy.invokeOrdered(msg.getBytes());
    YCSBMessage replyMsg = YCSBMessage.getObject(reply);
    return Status.OK;
  }

  @Override
  public Status read(
      String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    HashMap<String, byte[]> results = new HashMap<>();
    YCSBMessage request = YCSBMessage.newReadRequest(table, key, fields, results);
    byte[] reply = proxy.invokeUnordered(request.getBytes());
    YCSBMessage replyMsg = YCSBMessage.getObject(reply);
    return Status.OK;
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
    YCSBMessage msg = YCSBMessage.newUpdateRequest(table, key, map);
    byte[] reply = proxy.invokeOrdered(msg.getBytes());
    YCSBMessage replyMsg = YCSBMessage.getObject(reply);

    return Status.OK;
  }
}
