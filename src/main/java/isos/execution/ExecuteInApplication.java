package isos.execution;

import isos.execution.graph.DependencyGraph;
import isos.message.client.OrderedClientRequest;

import java.util.List;

@FunctionalInterface
public interface ExecuteInApplication {
  /**
   *
   * @param requests Strongly connected components, already sorted -> has to be executed sequentially in order
   * @param d Dependency Graph TODO Kai: Why is this passed to the execute function of the Application?
   */
  void execute(List<OrderedClientRequest> requests, DependencyGraph d);
}
