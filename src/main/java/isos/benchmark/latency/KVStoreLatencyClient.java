package isos.benchmark.latency;

import isos.benchmark.kvstore.KVStoreClient;
import isos.communication.client.QuorumNotReachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KVStoreLatencyClient {
  private final Logger logger;

  private final int clientId;
  private final double writeRatio; // 5%
  private final double conflictRatio; // 5%
  private final String GIVEN_KEY = "f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454";
  private final ThreadLocalRandom random = ThreadLocalRandom.current();

  private final KVStoreClient<String, String> client;

  // Result data
  private final RequestCompletedCallback singleRequestCompleted;

  private int failureCount = 0;
  private final LinkedList<Boolean> wasWriteRequest;
  private final LinkedList<Long> timestamps_ms;
  private final LinkedList<Long> latencies_us; // -1 if it was failure

  public KVStoreLatencyClient(
      int clientId,
      int writeRatioPercent,
      int conflictRatioPercent,
      RequestCompletedCallback notifySingleRequestCompleted) {
    this.logger = LoggerFactory.getLogger(String.format("Client%d", clientId));
    this.clientId = clientId;
    this.client = new KVStoreClient<>(clientId);
    this.conflictRatio = (double) conflictRatioPercent / 100;
    this.writeRatio = (double) writeRatioPercent / 100;
    this.singleRequestCompleted = notifySingleRequestCompleted;
    this.wasWriteRequest = new LinkedList<>();
    this.timestamps_ms = new LinkedList<>();
    this.latencies_us = new LinkedList<>();
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

  /**
   * Blocking operation to run the requests.
   *
   * @param requestCount
   * @throws IOException
   * @throws InterruptedException
   * @throws ClassNotFoundException
   */
  public void runRequests(int requestCount)
      throws IOException, InterruptedException, ClassNotFoundException {

    for (int i = 0; i < requestCount; i++) {
      try {
        String key = getKey();
        if (random.nextDouble() < writeRatio) {
          // Write
          wasWriteRequest.add(true);
          var latency = executeRequest(key, "asdf");
          timestamps_ms.add(System.currentTimeMillis());
          latencies_us.add(latency);
        } else {
          // Read
          wasWriteRequest.add(false);
          var latency = executeRequest(key, null);
          timestamps_ms.add(System.currentTimeMillis());
          latencies_us.add(latency);
        }
      } catch (TimeoutException e) {
        logger.warn("Timeout reached for request {} of client {}", i, this.clientId);
        this.failureCount++;
        timestamps_ms.add(System.currentTimeMillis());
        latencies_us.add(-1L);
      } catch (QuorumNotReachedException e) {
        logger.warn(
            "Quorum of same replies was not reached for request {} of client {}", i, this.clientId);
        this.failureCount++;
        timestamps_ms.add(System.currentTimeMillis());
        latencies_us.add(-1L);
      }
      this.singleRequestCompleted.onRequestCompleted(i + 1);
    }
    logger.info("Client {} is done!", this.clientId);
  }

  /**
   * @return The time required to execute the request in nanoseconds.
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
    return TimeUnit.NANOSECONDS.toMicros(endTime - startTime);
  }

  public List<LatencyBenchmarkResult> getBenchmarkResult() throws IllegalStateException {
    var writeRequestSize = wasWriteRequest.size();
    var latenciesSize = latencies_us.size();
    if (writeRequestSize != latenciesSize) {
      throw new IllegalStateException(
          String.format(
              "Size of writeRequestSize %d is different from size of latencies %d, there is a bug in the result collection",
              writeRequestSize, latenciesSize));
    }
    return IntStream.range(0, latenciesSize)
        .mapToObj(
            (i ->
                new LatencyBenchmarkResult(
                    wasWriteRequest.get(i), timestamps_ms.get(i), latencies_us.get(i))))
        .collect(Collectors.toList());
  }
}
