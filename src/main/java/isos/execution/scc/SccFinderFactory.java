package isos.execution.scc;

public class SccFinderFactory {
  public static SccFinder createSccFinder(SccStrategy strategy) {
    return switch (strategy) {
      case SEQUENTIAL_TARJAN -> new TarjanSCC();
      case CONCURRENT_TARJAN -> new ConcurrentTarjanSCC();
    };
  }
}
