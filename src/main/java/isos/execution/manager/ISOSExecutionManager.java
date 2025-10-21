package isos.execution.manager;


import isos.execution.CommittedCommand;

public interface ISOSExecutionManager extends Runnable {

  boolean submitCommittedRequest(CommittedCommand r);
}
