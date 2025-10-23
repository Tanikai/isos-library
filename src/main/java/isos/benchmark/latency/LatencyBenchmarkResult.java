package isos.benchmark.latency;

public record LatencyBenchmarkResult(
        int latency,
        boolean wasWrite
) {};
