package isos.benchmark.latency;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The KVStoreLatencyBenchmark runs clientCount threads that each send requestCount sequential
 * requests to a KV Store application running on BFT-SMaRt.
 */
public class KVStoreLatencyBenchmark {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final int clientGroupId;
  private final int clientCount;
  private final int requestCount;
  private final int conflictRatioPercent;
  private final int writeRatioPercent;
  private final int benchmarkTimeoutSecs = 60;
  private final Path outputDir;
  private final String benchmarkName;

  private final KVStoreLatencyClient[] clients;
  private final Map<Integer, List<LatencyBenchmarkResult>> results;

  ExecutorService executor;

  private final CountDownLatch startLatch;
  private final CountDownLatch endLatch;

  public static void main(String[] args) {
    Map<String, String> options = new HashMap<>();

    // parse arguments
    for (int i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg.startsWith("--")) {
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
    String benchmarkName = options.get("benchmarkName");
    if (benchmarkName == null) {
      throw new RuntimeException("benchmarkName has to be defined");
    }
    int clientGroupId = Integer.parseInt(options.get("groupId"));
    int clientCount = Integer.parseInt(options.get("clientCount"));
    int requestCount = Integer.parseInt(options.get("requestCount"));
    int writeRatioPercent = Integer.parseInt(options.get("writeRatioPercent"));
    int conflictRatioPercent = Integer.parseInt(options.get("conflictRatioPercent"));
    String outputDir = options.get("outputDir");
    if (outputDir == null) {
      throw new RuntimeException("outputDir has to be defined");
    }

    Path path = Paths.get(outputDir);

    try {
      if (!Files.exists(path)) {
        Files.createDirectories(path);
      }
    } catch (IOException e) {
      System.err.printf("Failed to create output directory %s: %s%n", path, e.getMessage());
    }

    var benchmark =
        new KVStoreLatencyBenchmark(
            clientGroupId,
            clientCount,
            requestCount,
            writeRatioPercent,
            conflictRatioPercent,
            outputDir,
            benchmarkName);
    benchmark.runBenchmark();

    System.exit(0);
  }

  public KVStoreLatencyBenchmark(
      int clientGroupId,
      int clientCount,
      int requestCount,
      int writeRatioPercent,
      int conflictRatioPercent,
      String outputDir,
      String benchmarkName) {
    this.clientGroupId = clientGroupId;
    this.clientCount = clientCount;
    this.requestCount = requestCount;
    this.writeRatioPercent = writeRatioPercent;
    this.conflictRatioPercent = conflictRatioPercent;
    this.outputDir = Paths.get(outputDir);
    this.benchmarkName = benchmarkName;

    this.clients = new KVStoreLatencyClient[clientCount];
    this.results = new ConcurrentHashMap<>();
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
            logger.info("Start client {}", clientId);
            var client = this.clients[clientId];
            try {
              this.startLatch.await();
              client.runRequests(this.requestCount);

              // Store results
              List<LatencyBenchmarkResult> results = client.getBenchmarkResult();
              OptionalDouble averageMs =
                  results.stream().mapToLong(LatencyBenchmarkResult::latency).average();
              this.results.put(clientId, results);
              logger.info("Average latencies: {}", averageMs.orElse(-1D));
            } catch (InterruptedException e) {
              logger.info("Thread was interrupted, stopping");
            } catch (IOException e) {
              logger.error("IOException in client {}: {}", clientId, e.getMessage());
            } catch (ClassNotFoundException e) {
              logger.error("ClassNotFoundException in client {}: {}", client, e.getMessage());
            } catch (Exception e) {
              logger.error("Unhandled exception: {}", e.getMessage());
            }
            this.endLatch.countDown();
          });
    }
  }

  private String getFileName() {
    return String.format("%s_%d.csv", this.benchmarkName, this.clientGroupId);
  }

  /** Overwrites a potentially already existing file. */
  private void writeHeader() {
    File outputFile =
        this.outputDir
            .resolve(this.getFileName())
            .toFile();
    File parentDir = outputFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      parentDir.mkdirs();
    }
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, false))) {
      // Header
      writer.println("latency,wasWrite,clientId");
    } catch (IOException e) {
      logger.error("Failed to open results file");
    }
  }

  /**
   * Appends the results to the existing file.
   *
   * @param clientId
   * @param results
   */
  private void writeResults(int clientId, List<LatencyBenchmarkResult> results) {
    File outputFile =
        this.outputDir
            .resolve(this.getFileName())
            .toFile();

    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
      for (var line : results) {
        writer.printf("%d,%b,%d\n", line.latency(), line.wasWrite(), clientId);
      }
    } catch (IOException e) {
      logger.error("Failed to open results file");
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
      if (!countReachedZero) {
        logger.error("Timeout reached before all clients finished. Canceling ExecutorService");
        this.executor.shutdownNow();
        return;
      }
      logger.info("Benchmark successful");
      // TODO Kai: how to print results? Per client? Summary per region?

      this.writeHeader();
      for (Integer clientId : this.results.keySet().stream().sorted().toList()) {
        var singleResult = this.results.get(clientId);
        this.writeResults(clientId, singleResult);
      }

      logger.info("Stored results in directory");

    } catch (InterruptedException e) {
      logger.warn("Interrupted while executing benchmark.");
    }
  }
}
