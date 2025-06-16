package isos.consensus;

public class TimeoutConfiguration {
  long delta;
  long proposeTimeout;
  long commitTimeout;
  long viewChangeTimeout;
  long viewChangeCommitTimeout;
  long queryExecTimeout;

  public TimeoutConfiguration(long delta) {
    this.setDelta(delta);
  }

  /**
   * Requirement: Delta (∆) is the maximum one-way delay between replica (Section E, Progress
   * Guarantee)
   *
   * @param delta
   */
  public void setDelta(long delta) {
    this.delta = delta;
    // See pseudocode line 9
    this.proposeTimeout = 2 * delta;
    this.commitTimeout = 9 * delta;
    this.viewChangeTimeout = 3 * delta;
    this.viewChangeCommitTimeout = 3 * delta;
    this.queryExecTimeout = 4 * delta;
  }

  public long getDelta() {
    return delta;
  }

  public long getProposeTimeout() {
    return proposeTimeout;
  }

  public long getCommitTimeout() {
    return commitTimeout;
  }

  public long getViewChangeTimeout() {
    return viewChangeTimeout;
  }

  public long getViewChangeCommitTimeout() {
    return viewChangeCommitTimeout;
  }

  public long getQueryExecTimeout() {
    return queryExecTimeout;
  }
}
