package isos.execution;

@FunctionalInterface
public interface ExecutableRequestReceiver {
  /**
   * Forwards a request to the execution. Has to be a thread safe call, because it can be called
   * by multiple agreement slots concurrently.
   * @param r
   */
  void forwardRequestToExecution(CommittedCommand r);
}
