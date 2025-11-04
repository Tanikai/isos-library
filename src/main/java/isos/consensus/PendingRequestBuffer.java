package isos.consensus;

import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestBatch;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PendingRequestBuffer {

  private final ConcurrentLinkedQueue<OrderedClientRequest> pendingRequests;
  private final int batchTimeout;
  private final long batchTimeoutNanos;
  private final int maxBatchCount;
  private final int maxBatchBytes;

  //
  private final ScheduledExecutorService scheduledExecutor;

  private long lastRequestAdded;

  private final ReentrantLock messagesLock = new ReentrantLock();
  private final Condition batchReadyCond = messagesLock.newCondition();

  public PendingRequestBuffer(int batchTimeoutMillis, int maxBatchCount, int maxBatchBytes) {
    this.pendingRequests = new ConcurrentLinkedQueue<>();
    this.maxBatchCount = maxBatchCount;
    this.maxBatchBytes = maxBatchBytes;
    this.batchTimeout = batchTimeoutMillis;
    this.batchTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(batchTimeoutMillis);

    if (batchTimeoutMillis > 0) {
      this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

      // Unblock propose thread in intervals to notify
      this.scheduledExecutor.scheduleAtFixedRate(
          () -> {
            long now = System.nanoTime();
            // If the elapsed time from the last added request is smaller than our batch timeout,
            // wait again.
            // Uses polling, but for high request frequencies (e.g., benchmarks), this should be
            // more efficient than canceling and rescheduling the timeout event.
            if ((now - lastRequestAdded) < batchTimeoutNanos) {
              return;
            }
            // There is potential for a race condition between this notifyBatchReady and
            // addPendingRequest so that notifyBatchReady is called twice, but as there is only a
            // single consumer that is woken up and taking out the requests is done via a concurrent
            // structure, this should be negligible.
            this.notifyBatchReady();
          },
          batchTimeoutMillis / 2, // use half the timeout for maximum of 1.5x batchTimeout flushing
          batchTimeoutMillis / 2,
          TimeUnit.MILLISECONDS);
    } else {
      this.scheduledExecutor = null;
    }
  }

  public void addPendingRequest(OrderedClientRequest newRequest) throws IllegalStateException {
    this.pendingRequests.add(newRequest);
    this.lastRequestAdded = System.nanoTime();

    if (this.isNextBatchReady()) {
      notifyBatchReady();
    }
  }

  /**
   * @return
   * @throws InterruptedException
   */
  public ClientRequestBatch awaitPendingRequests() throws InterruptedException {
    this.messagesLock.lock();
    try {
      if (this.isNextBatchReady()) {
        return this.getPendingRequests();
      }

      // If no requests are ready, wait until the batch is ready (full batch, or periodic timeout
      // reached)
      this.batchReadyCond.await();

      // If batchReadyCond is triggered, we can create a new batch
      return this.getPendingRequests();
    } finally {
      this.messagesLock.unlock();
    }
  }

  /**
   * Returns pending requests until pending requests is empty, maxBatchSize or maxBatchBytes is
   * reached
   *
   * @return
   */
  private ClientRequestBatch getPendingRequests() {
    List<OrderedClientRequest> batch = new LinkedList<>();

    int batchCount = 0;
    int batchSize = 0;
    while (!pendingRequests.isEmpty()) {
      if (batchCount >= this.maxBatchCount) break;
      if (batchSize >= this.maxBatchBytes) break;

      var cmd = pendingRequests.poll();
      if (cmd == null) break;
      batch.add(cmd);
      batchCount++;
      batchSize += cmd.command().length;
    }

    return new ClientRequestBatch(batch);
  }

  private boolean isNextBatchReady() {
    // if we have no requests, the batch is not ready
    if (this.pendingRequests.isEmpty()) {
      return false;
    }

    // If we have no batch timeout, the next batch is always ready
    if (this.batchTimeout <= 0) {
      return true;
    }

    if (this.pendingRequests.size() >= this.maxBatchCount) {
      return true;
    }

    if (this.pendingRequests.stream().mapToInt((entry) -> entry.command().length).sum()
        >= this.maxBatchBytes) {
      return true;
    }

    return false;
  }

  private void notifyBatchReady() {
    this.messagesLock.lock();
    try {
      // If we have at least 1 message, we can create a partial batch
      if (!this.pendingRequests.isEmpty()) {
        this.batchReadyCond.signalAll();
      }
    } finally {
      this.messagesLock.unlock();
    }
  }
}
