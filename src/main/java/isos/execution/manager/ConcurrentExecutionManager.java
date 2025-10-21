package isos.execution.manager;

import isos.execution.CommittedCommand;

public class ConcurrentExecutionManager implements ISOSExecutionManager, Runnable {

  @Override
  public boolean submitCommittedRequest(CommittedCommand r) {
    return false;
  }

  @Override
  public void run() {

  }
}
