package isos.graph;

import isos.message.ExecuteMessage;

@FunctionalInterface
public interface ExecutableRequestReceiver {
  void forwardRequestToExecution(ExecuteMessage r);
}
