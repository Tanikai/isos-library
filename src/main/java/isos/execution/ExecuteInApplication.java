package isos.execution;

import isos.message.replica.ClientRequestBatch;

@FunctionalInterface
public interface ExecuteInApplication {
  /**
   * @param request The client request that can be executed by the application.
   */
  void execute(ClientRequestBatch request);
}
