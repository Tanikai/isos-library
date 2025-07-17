package isos.graph;

@FunctionalInterface
public interface ExecutableRequestReceiver {
  void forwardRequestToExecution(ExecuteMessage r);
}
