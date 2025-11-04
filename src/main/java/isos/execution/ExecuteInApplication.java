package isos.execution;

import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestContainer;

@FunctionalInterface
public interface ExecuteInApplication {
  /**
   * @param request The client request that can be executed by the application.
   */
  void execute(ClientRequestContainer request);
}
