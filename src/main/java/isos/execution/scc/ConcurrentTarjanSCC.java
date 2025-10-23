package isos.execution.scc;

import isos.consensus.model.SequenceNumber;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** Concurrent Tarjan SCC implementation based on Lowe (2014). */
public class ConcurrentTarjanSCC implements SccFinder {

  private final ExecutorService executor;

  public ConcurrentTarjanSCC() {
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public List<Set<SequenceNumber>> getSCC(
      Map<SequenceNumber, Set<SequenceNumber>> adjList, Set<SequenceNumber> vertices) {
    List<Set<SequenceNumber>> result = new ArrayList<>();

    // Create necessary data structures
    var nodes = getNodes(vertices, adjList);
    var suspended = new Suspended();
    // TODO Kai: start search thread here
    // A search is started at an arbitrary node startNode that has not yet been considered by any
    // other search.
    this.executor.submit(
        () -> {
          try {
            new Search(nodes.get(vertices.stream().toList().getFirst()), suspended);
          } catch (InterruptedException e) {
            // TODO: Error handling
          }
        });

    return result;
  }

  private ConcurrentMap<SequenceNumber, Node> getNodes(
      Set<SequenceNumber> vertices, Map<SequenceNumber, Set<SequenceNumber>> adjList) {
    ConcurrentMap<SequenceNumber, Node> nodes = new ConcurrentHashMap<>();

    // First, create nodes
    vertices.stream()
        .forEach(
            (seqNum) -> {
              nodes.put(seqNum, new Node(seqNum));
            });

    // Then, add the unexplored edges
    vertices.parallelStream()
        .forEach(
            (seqNum) -> {
              Set<SequenceNumber> deps = adjList.get(seqNum);
              var node = nodes.get(seqNum);
              node.unexploredEdges = deps.stream().map(nodes::get).collect(Collectors.toList());
            });

    return nodes;
  }

  public static class Node {
    public SequenceNumber seqNum;
    public int index; // Sequence counter, corresponding to order in which nodes were encountered
    public int lowlink;
    public NodeStatus status;
    public List<Node> unexploredEdges; // Edges which have not yet been expanded
    public Search search;
    public List<Search> blocked; // Searches that have encountered this node and are blocked on it
    public CountDownLatch completedLatch;

    /**
     * Updates low-link to indicate that node with index update is reachable from this node
     *
     * @param update
     */
    public void updateLowlink(int update) {
      this.lowlink = Math.min(this.lowlink, update);
    }

    public Node(SequenceNumber seqNum) {
      this.seqNum = seqNum;
      this.unexploredEdges = new LinkedList<>();
      this.blocked = new LinkedList<>();
      this.completedLatch = new CountDownLatch(1);
    }
  }

  public enum NodeStatus {
    Unseen,
    InStack,
    Complete
  }

  // Operations on child in lines 20-28 should be done atomically

  public static class Search {
    private int index = 0;
    private Deque<Node> tarjanStack = new ArrayDeque<>();
    private Deque<Node> controlStack = new ArrayDeque<>();
    private Node waitingFor = null;
    private SearchStatus status;
    private Suspended suspended;

    private List<Set<Node>> resultSccs = new LinkedList<>();

    public Search(Node startNode, Suspended suspended) throws InterruptedException {
      this.suspended = suspended;
      // Starting from Pseudocode line 16
      addNode(startNode);
      this.status = SearchStatus.InProgress;
      while (!controlStack.isEmpty()) {
        // TODO: We should try selecting a child node that is not in-progress in a different search,
        // if one exists.
        var node = controlStack.getFirst();

        // This is the test whether n is complete:
        if (!node.unexploredEdges.isEmpty()) { // if node has unexplored edge to child -> test
          // From here, operations should be done atomically
          // Line 20 ff
          var child = node.unexploredEdges.removeFirst();
          if (child.status == NodeStatus.InStack) {
            addNode(child);
          } else if (child.search == this) {
            // child is in tarjanStack of this search
            node.updateLowlink(child.index);
          } else { // child in-progress of different search
            // TODO: Testing whether node is complete and updating blocked has to be done atomically
            this.status = SearchStatus.Suspended;
            child.blocked.add(this);
            this.waitingFor = child; // Not in pseudocode but has to be included
            // Suspend waiting for child to complete
            child.completedLatch.await();
          }
          // End atomical operation
          // otherwise, child is complete, nothing to do
        } else { // backtrack from node
          controlStack.pop();
          if (!controlStack.isEmpty()) {
            controlStack.getFirst().updateLowlink(node.lowlink);
          }
          if (node.lowlink == node.index) {
            Set<Node> newScc = new HashSet<>();
            Node w;
            do {
              w = tarjanStack.pop();
              newScc.add(w);
              w.status = NodeStatus.Complete;
              // Unblock any searches suspended on w
              w.completedLatch.countDown();
            } while (w != node);
            this.resultSccs.add(newScc);
          }
        }
      }
      this.status = SearchStatus.Complete;
    }

    private void addNode(Node newNode) {
      newNode.index = index;
      newNode.lowlink = index;
      this.index++;
      newNode.search = this;
      this.controlStack.push(newNode);
      this.tarjanStack.push(newNode);
      newNode.status = NodeStatus.InStack;
    }
  }

  public enum SearchStatus {
    InProgress,
    Suspended,
    Pending,
    Complete
  }

  /** Records which searches are blocked on with others. */
  public class Suspended {

    /**
     * Mapping: Waiting Search -> Waited on Search
     *
     * <p>The suspended map itself must be thread-safe.
     */
    private ConcurrentMap<Search, Search> suspendedSearches;

    public Suspended() {
      suspendedSearches = new ConcurrentHashMap<>();
    }

    public void suspend(Search s, Node n) {
      // Search s is blocked by a node n of s' (s' = n.search)
      var sPrime = n.search;

      // Transitively follow the suspended map to see if it includes a blocking path from s' to s
      Optional<List<Search>> path = this.findPath(sPrime, s);
      // If there exists a path from sPrime to s, we would effectively create a deadlock due to the
      // circular dependency on searches that are waited on

      if (path.isEmpty()) {
        // No blocking path fround, s -> s' is added to the suspended map
        this.suspendedSearches.put(s, sPrime);
        return;
      }

      // If a path is found, nodes are transferred from s' to s, and s is resumed.
      this.transferSearches(s, path.get());

      // In detail: the blocking path is traversed in order, starting with the search s' that
      // directly blocks s.
    }

    /**
     * Follow the suspended map to see if it includes a blocking path from s' to s. Builds the path
     * followed, and continues until it reaches either an unsuspended search, or the target.
     *
     * @param start
     * @param target
     * @return Optional.empty() if there is no path, `List<Search>` if there is a path.
     */
    private Optional<List<Search>> findPath(Search start, Search target) {
      Search current = start;
      List<Search> path = new LinkedList<>();
      path.add(start);

      while (suspendedSearches.containsKey(current) && current != target) {
        // Follow the suspended search
        current = suspendedSearches.get(current);
        if (current != target) {
          path.add(current);
        }
      }

      if (current != target) {
        return Optional.empty();
      } else {
        return Optional.of(path);
      }
    }

    private void transferSearches(Search destination, List<Search> path) {
      if (path.isEmpty()) {
        return;
      }

      // TODO: Graph traversal

      // The blocking path is traversed in order, starting with the search s' that directly blocks
      // s.
      var search = path.getFirst();

      // Traverse tarjan stack of s_b

      // Each node is tranferred to stacks of s, and its search, index, and lowlink are updated.

    }
  }
}
