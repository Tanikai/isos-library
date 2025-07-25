package isos.execution;

@FunctionalInterface
public interface ExecutableRequestReceiver {
  void forwardRequestToExecution(ExecuteMessage r);
}
