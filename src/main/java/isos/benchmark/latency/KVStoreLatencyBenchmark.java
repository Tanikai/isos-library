package isos.benchmark.latency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The KVStoreLatencyBenchmark runs clientCount threads that each send requestCount sequential
 * requests to a KV Store application running with the ISOS consensus algorithm.
 */
public class KVStoreLatencyBenchmark {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int clientGroupId;
  private final int clientCount;
  private final int requestCount;
  private final int conflictRatioPercent;
  private final int writeRatioPercent;
  private final int benchmarkTimeoutSecs = 60;

  private final KVStoreLatencyClient[] clients;

  ExecutorService executor;

  private final CountDownLatch startLatch;
  private final CountDownLatch endLatch;

  public static void main(String[] args) {
    Map<String, String> options = new HashMap<>();

    // parse arguments
    for (int i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg.startsWith("-groupId")) {
        String[] parts = arg.substring(2).split("=", 2);
        if (parts.length == 2) {
          options.put(parts[0], parts[1]);
        } else {
          System.err.println("Warning: Malformed argument: " + arg);
          return;
        }
      }
    }

    // Throws exception if argument cannot be parsed
    int clientGroupId = Integer.parseInt(options.get("groupId"));
    int clientCount = Integer.parseInt(options.get("clientCount"));
    int requestCount = Integer.parseInt(options.get("requestCount"));
    int writeRatioPercent = Integer.parseInt(options.get("writeRatioPercent"));
    int conflictRatioPercent = Integer.parseInt(options.get("conflictRatioPercent"));

    var benchmark =
        new KVStoreLatencyBenchmark(
            clientGroupId, clientCount, requestCount, writeRatioPercent, conflictRatioPercent);
    benchmark.runBenchmark();
  }

  public KVStoreLatencyBenchmark(
      int clientGroupId,
      int clientCount,
      int requestCount,
      int writeRatioPercent,
      int conflictRatioPercent) {
    this.clientGroupId = clientGroupId;
    this.clientCount = clientCount;
    this.requestCount = requestCount;
    this.writeRatioPercent = writeRatioPercent;
    this.conflictRatioPercent = conflictRatioPercent;

    this.clients = new KVStoreLatencyClient[clientCount];
    this.executor = Executors.newFixedThreadPool(clientCount);
    this.startLatch = new CountDownLatch(1);
    this.endLatch = new CountDownLatch(clientCount);

    var groupPrefix = clientGroupId * 10000;

    // Create worker threads for client
    for (var i = 0; i < clientCount; i++) {
      this.clients[i] =
          new KVStoreLatencyClient(
              groupPrefix + i, this.writeRatioPercent, this.conflictRatioPercent);

      int clientId = i;
      this.executor.submit(
          () -> {
            var client = this.clients[clientId];
            try {
              client.runRequests(this.startLatch, this.requestCount);

              var results = client.getBenchmarkResult();
              // TODO Kai: How to store results for further analysis?
            } catch (InterruptedException e) {
              logger.info("Thread was interrupted, stopping");
            } catch (IOException e) {
              logger.error("IOException in client {}: {}", clientId, e.getMessage());
            } catch (ClassNotFoundException e) {
              logger.error("ClassNotFoundException in client {}: {}", client, e.getMessage());
            }
          });
    }
  }

  public void runBenchmark() {
    logger.info(
        "Starting benchmark with {} clients, {} requests each, {}% write rate, {}% conflict rate",
        clientCount, requestCount, writeRatioPercent, conflictRatioPercent);
    this.startLatch.countDown();

    logger.info("Waiting for benchmark to end, timeout of {} seconds", benchmarkTimeoutSecs);
    try {
      boolean countReachedZero = this.endLatch.await(benchmarkTimeoutSecs, TimeUnit.SECONDS);
      if (countReachedZero) {
        logger.info("Benchmark successful");
        // TODO Kai: how to print results? Per client? Summary per region?
        return;
      }

      logger.error("Timeout reached before all clients finished. Canceling ExecutorService");
      this.executor.shutdownNow();
    } catch (InterruptedException e) {
      logger.warn("Interrupted while executing benchmark.");
    }
  }
}
