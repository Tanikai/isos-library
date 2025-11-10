package isos.benchmark.latency;

public record LatencyBenchmarkResult(
        boolean wasUpdateOperation,
        /**
         * timestamp_ms is the currentTimeMillis when the record is inserted
         */
        long timestamp_ms,
        long latency_us
) {};
