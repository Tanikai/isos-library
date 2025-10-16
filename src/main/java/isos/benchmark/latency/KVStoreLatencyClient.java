package isos.benchmark.latency;

import isos.benchmark.kvstore.KVStoreClient;
import isos.communication.client.QuorumNotReachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KVStoreLatencyClient {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int clientId;
  private final double writeRatio; // 5%
  private final double conflictRatio; // 5%
  private final String GIVEN_KEY = "f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454";
  private final ThreadLocalRandom random = ThreadLocalRandom.current();

  private final KVStoreClient<String, String> client;

  // Result data
  private int failureCount = 0;
  private LinkedList<Boolean> wasWriteRequest;
  private LinkedList<Long> latencies; // -1 if it was failure

  public KVStoreLatencyClient(int clientId, int writeRatioPercent, int conflictRatioPercent) {
    this.clientId = clientId;
    this.client = new KVStoreClient<>(clientId);
    this.conflictRatio = (double) conflictRatioPercent / 100;
    this.writeRatio = (double) writeRatioPercent / 100;
    this.logger.info("Initialized KVStoreLatencyClient");
  }

  public String getKey() {
    if (random.nextDouble() < conflictRatio) {
      // Pick a random predetermined key
      return GIVEN_KEY;
    } else {
      // Generate a unique key
      // https://www.uuidgenerator.net/dev-corner/java
      return UUID.randomUUID().toString();
    }
  }

  public void runRequests(CountDownLatch startSignal, int requestCount)
      throws IOException, InterruptedException, ClassNotFoundException {
    startSignal.await();

    for (int i = 0; i < requestCount; i++) {
      // TODO Kai: How to determine read/write ratio?
      try {
        String key = getKey();
        if (random.nextDouble() < writeRatio) {
          // Write
          wasWriteRequest.add(true);
          var latency = executeRequest(key, "asdf");
          latencies.add(latency);
        } else {
          // Read
          wasWriteRequest.add(false);
          var latency = executeRequest(key, null);
          latencies.add(latency);
        }
      } catch (TimeoutException e) {
        logger.warn("Timeout reached for request {} of client {}", i, this.clientId);
        this.failureCount++;
        latencies.add(-1L);
      } catch (QuorumNotReachedException e) {
        logger.warn(
            "Quorum of same replies was not reached for request {} of client {}", i, this.clientId);
        this.failureCount++;
        latencies.add(-1L);
      }
    }
  }

  /**
   * @return The time required to execute the request in milliseconds.
   */
  public long executeRequest(String key, String value)
      throws IOException, TimeoutException, QuorumNotReachedException, ClassNotFoundException {
    long startTime = System.nanoTime();
    if (value == null) {
      this.client.get(key);
    } else {
      this.client.put(key, value);
    }
    long endTime = System.nanoTime();
    return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
  }

  public List<Map.Entry<Long, Boolean>> getBenchmarkResult() throws IllegalStateException {
    var writeRequestSize = wasWriteRequest.size();
    var latenciesSize = latencies.size();
    if (writeRequestSize != latenciesSize) {
      throw new IllegalStateException(
          String.format(
              "Size of writeRequestSize %d is different from size of latencies %d, there is a bug in the result collection",
              writeRequestSize, latenciesSize));
    }
    return IntStream.range(0, latenciesSize)
        .mapToObj((i -> Map.entry(latencies.get(i), wasWriteRequest.get(i))))
        .collect(Collectors.toList());
  }
}
