package isos.consensus.model;

public class TimeoutConfiguration {
  long deltaMillis;
  long proposeTimeout;
  long commitTimeout;
  long viewChangeTimeout;
  long viewChangeCommitTimeout;
  long queryExecTimeout;

  public TimeoutConfiguration(long delta) {
    this.setDeltaMillis(delta);
  }

  /**
   * Requirement: Delta (∆) is the maximum one-way delay between replica (Section E, Progress
   * Guarantee)
   *
   * @param deltaMillis Delay in milliseconds
   */
  public void setDeltaMillis(long deltaMillis) {
    this.deltaMillis = deltaMillis;
    // See pseudocode line 9
    this.proposeTimeout = 2 * this.deltaMillis;
    this.commitTimeout = 9 * this.deltaMillis;
    this.viewChangeTimeout = 3 * this.deltaMillis;
    this.viewChangeCommitTimeout = 3 * this.deltaMillis;
    this.queryExecTimeout = 4 * this.deltaMillis;
  }

  public long getDeltaMillis() {
    return deltaMillis;
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

  public long getTimeoutDurationByType(ISOSTimeoutType timeoutType) {
    return switch (timeoutType) {
      case NULL -> 0L;
      case PROPOSE -> this.proposeTimeout;
      case COMMIT -> this.commitTimeout;
      case VIEWCHANGE -> this.viewChangeTimeout;
      case VIEWCHANGE_COMMIT -> this.viewChangeCommitTimeout;
      case QUERY_EXEC -> this.queryExecTimeout;
    };
  }
}
