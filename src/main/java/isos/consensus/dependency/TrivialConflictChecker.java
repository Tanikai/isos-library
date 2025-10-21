package isos.consensus.dependency;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.execution.CommittedCommand;
import isos.execution.graph.DependencyGraph;
import isos.execution.scc.SccFinder;
import isos.execution.scc.SccUtils;
import isos.message.client.OrderedClientRequest;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class TrivialConflictChecker implements ConflictChecker {

  /**
   * Own representation of current agreement slots of all replicas. Kept in sync via
   * addClientRequest, overwriteClientRequest, and updateCommitedRequest.
   */
  private final Map<SequenceNumber, OrderedClientRequest> agreementSlots;

  private final Map<SequenceNumber, Set<SequenceNumber>> currentDependencyGraph;

  private final SccFinder sccFinder;

  // Conflict predicates
  BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict;
  BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict;

  private final ReentrantLock graphLock;

  public TrivialConflictChecker(
      SccFinder sccFinder,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> defaultConflict,
      BiPredicate<OrderedClientRequest, OrderedClientRequest> applicationConflict) {
    this.agreementSlots = new HashMap<>();
    this.currentDependencyGraph = new HashMap<>();
    this.sccFinder = sccFinder;
    this.defaultConflict = defaultConflict;
    this.applicationConflict = applicationConflict;

    this.graphLock = new ReentrantLock();
  }

  @Override
  public void addClientRequest(SequenceNumber slot, OrderedClientRequest r, DependencySet deps) {
    this.graphLock.lock();
    try {
      this.agreementSlots.put(slot, r);
      this.currentDependencyGraph.put(slot, deps.dependencies());
    } finally {
      this.graphLock.unlock();
    }
  }

  @Override
  public void overwriteClientRequest(SequenceNumber slot, OrderedClientRequest r) {
    this.graphLock.lock();
    try {
      this.agreementSlots.put(slot, r);
    } finally {
      this.graphLock.unlock();
    }
  }

  @Override
  public void updateCommitedRequest(CommittedCommand c) {
    this.graphLock.lock();
    try {
      this.currentDependencyGraph.put(c.seqNum(), c.depSet().dependencies());
    } finally {
      this.graphLock.unlock();
    }
  }

  /**
   * Pseudocode line 66, 67
   *
   * <p>Trivial Implementation -> Recalculate reachability in dependency graph with DFS / BFS every
   * time.
   *
   * <p>This function is in the hot path, so performance is critical here.
   *
   * @param r Client Request to get the
   * @return All agreement slots that have a DepPropose message (i.e. non-null)
   */
  @Override
  public DependencySet getCompactDependencySet(SequenceNumber seqNum, OrderedClientRequest r) {
    this.graphLock.lock();
    try {
      // Requirement: For the dependency set, the coordinator takes all known requests from both its
      // own and other replicas' agreement slots into account (see paper sec. B).

      // Optimization: Evaluate whether fork/join could be applicable here -> might be good for
      // large dependency sets
      // Answer: parallelStream() uses fork/join in background

      Set<SequenceNumber> candidateDependencies =
          this.agreementSlots
              .entrySet()
              // allow for parallelStream() as well, as dependencies can be calculated independently
              .parallelStream()
              // if they conflict, return the sequence number, else return null for "no conflict"
              .map(
                  entry -> {
                    if (this.defaultConflict
                        .or(this.applicationConflict)
                        .test(r, entry.getValue())) {
                      return entry.getKey();
                    } else {
                      return null;
                    }
                  })
              .filter(Objects::nonNull) // filter out the "no conflict"s
              .collect(Collectors.toSet());

      // Requirement: To limit the size of the set, the coordinator for each replica only includes
      // the sequence number of the **latest conflicting request**. -> Compact Dependency Set
      // How is latest conflicting request defined?
      // How can I get the sequence number of only the last conflicting request?
      // Approach 1: Get all conflicts, then filter out the indirect conflicts
      // -> "All other dependencies have to be reachable with only the direct conflicts"
      // -> Transitive Reduction
      // -> Not of the whole graph, but rather only the edges of the new request
      // Is

      return new DependencySet(
          TrivialConflictChecker.removeRedundantDependencies(
              this.sccFinder, this.currentDependencyGraph, seqNum, candidateDependencies));
    } finally {
      this.graphLock.unlock();
    }
  }

  /**
   * @param originalGraph The current dependency graph, used to determine the reachability.
   * @param newVertex The sequence number of the newly added vertex
   * @param candidateDeps Full Dependency Set
   * @return Compact Dependency Set
   */
  public static Set<SequenceNumber> removeRedundantDependencies(
      SccFinder sccFinder,
      Map<SequenceNumber, Set<SequenceNumber>> originalGraph,
      SequenceNumber newVertex,
      Set<SequenceNumber> candidateDeps) {
    // Create a copy of the original graph and add the new vertex + outgoing edges
    Map<SequenceNumber, Set<SequenceNumber>> graph = new HashMap<>(originalGraph);
    graph.put(newVertex, candidateDeps);
    Set<SequenceNumber> vertices = graph.keySet();

    // We have a new, virtual vertex v. All destination vertices in newEdges have to be reachable
    // from v.

    // Because we are calculating the SCCs before deduplication and the new dependencies could
    // introduce new cycles,
    // we are adding the candidate dependencies to the original graph.
    var SCCs = sccFinder.getSCC(graph, vertices);

    // After we get the SCCs, we can build a DAG out of the super-vertices, containing multiple
    // SequenceNumbers.
    Map<SequenceNumber, Integer> sccLookup = SccUtils.buildSccLookup(SCCs);
    Integer newVertexSccId = sccLookup.get(newVertex);

    Map<Integer, Set<Integer>> sccDag = SccUtils.buildSccDAG(graph, sccLookup, SCCs);

    // First, we group the candidate edges by their SCC destination. Because any vertex in a SCC
    // can be reached from any other vertex, they can be counted as a same edge and grouped
    // together. Because we only need 1 edge to a SCC and determining the edge has to be
    // deterministic, we use the edge with the lowest SequenceNumber.

    // TODO Kai: would be interesting if there is any performance improvement with lowest / highest
    // SequenceNumber.
    // Hypothesis: Lowest SequenceNumber would intuitively have better performance, as other
    // replicas. The highest SequenceNumber might not have been propagated to all replicas, which
    // means that the dependency sets differ and a reconciliation path might be required. However,
    // if a coordinator has low traffic, it has the lowest sequence number, even though it might be
    // the newest request. -> Probably wouldn't have much impact and other optimizations are more
    // worth
    Map<Integer, List<SequenceNumber>> candPerScc =
        candidateDeps.stream().collect(Collectors.groupingBy(sccLookup::get));

    Map<Integer, SequenceNumber> singleCandPerScc =
        candPerScc.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().stream().min(Comparator.naturalOrder()).orElseThrow()));

    // The resulting map has the candidate edge with the SCC ID as the key and the corresponding
    // SequenceNumber for the original graph. Now, we can do the simple DAG reachability test to
    // remove the redundant dependencies.
    Set<Integer> compactSccDeps =
        singleCandPerScc.keySet().parallelStream()
            // if the scc is not reachable without the edge, add it to the compact dependency set
            .filter(depSccId -> !isSccReachableWithout(sccDag, newVertexSccId, depSccId))
            .collect(Collectors.toSet());

    // After filtering out the candidates, we can map it back to a normal dependency via
    // singleCandPerScc mapping
    Set<SequenceNumber> compactDeps =
        compactSccDeps.stream().map(singleCandPerScc::get).collect(Collectors.toSet());
    return compactDeps;
  }

  /**
   * DFS approach for reachability.
   *
   * <p>If no from->to edge exists, it is a standard reachability test.
   *
   * @param sccDAG
   * @param from The SCC ID
   * @param to The SCC ID
   * @return
   */
  public static boolean isSccReachableWithout(Map<Integer, Set<Integer>> sccDAG, int from, int to) {
    if (!sccDAG.containsKey(from)) {
      throw new IllegalArgumentException(String.format("Source SCC ID %d does not exist!", from));
    }

    if (!sccDAG.containsKey(to)) {
      throw new IllegalArgumentException(
          String.format("Destination SCC ID %d does not exist!", to));
    }

    if (from == to) {
      // if the from and to SequenceNumbers are in the same SCC, do not remove the edge
      return false;
    }

    if (!sccDAG.get(from).contains(to)) {
      throw new IllegalArgumentException(
          String.format("Direct edge between SCCs %d-->%d does not exist!", from, to));
    }

    Set<Integer> visited = new HashSet<>();
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(from);

    while (!stack.isEmpty()) {
      int current = stack.pop();
      if (current == to) {
        // We have reached the "to" vertex without the path. The vertex is reachable without the
        // "from" vertex.
        return true;
      }
      if (!visited.add(current)) {
        // returns false if set already contains current
        continue;
      }

      for (int neighbor : sccDAG.computeIfAbsent(current, (k) -> Collections.emptySet())) {
        if (current == from && neighbor == to) {
          // skip the candidate dependency that we want to potentially remove
          continue;
        }
        if (!visited.contains(neighbor)) {
          stack.push(neighbor);
        }
      }
    }

    // We did not find another path, so the edge is required
    return false;
  }
}
