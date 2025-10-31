package bftsmart.communication.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** */
public class PingHandler implements Runnable {
  private final Logger logger;

  private final int ownReplicaId;
  private final ByteArraySender msgSender;
  private final int pingIntervalMillis;

  // Determining Round Trip Time (RTT)
  // We are using Exponentially Weighted Moving Average (EWMA), used in TCP
  // TODO Kai: maybe larger alpha due to low count of ping messages?
  private final double ewmaAlpha;
  private final AtomicLong ewmaMillis = new AtomicLong(-1);
  private byte[] lastPingNonce;
  private long lastPingNanos;

  public PingHandler(int ownReplicaId, int remoteReplicaId, ByteArraySender msgSender, double ewmaAlpha, Integer pingIntervalMillis) {
    this.ownReplicaId = ownReplicaId;
    this.msgSender = msgSender;
    this.logger =
        LoggerFactory.getLogger(
            String.format("PingHandler %d->%d", this.ownReplicaId, remoteReplicaId));
    this.pingIntervalMillis = pingIntervalMillis;
    this.ewmaAlpha = ewmaAlpha;
  }

  @Override
  public void run() {
//    try {
//      long initialDelayMs = ThreadLocalRandom.current().nextInt(5000);
//      Thread.sleep(initialDelayMs);
//    } catch (InterruptedException e) {
//      logger.error("Interrupted while waiting initial delay ms for ping message, exiting");
//      return;
//    }

    byte[] msgBytes;
    while (!Thread.currentThread().isInterrupted()) {
      // We want to generate a secure nonce to
      lastPingNonce = generateSecureNonce(16);
      lastPingNanos = System.nanoTime();
      var msg = new PingMessage(this.ownReplicaId, lastPingNonce, false);

      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(msg);
        msgBytes = bos.toByteArray();
      } catch (IOException e) {
        logger.error("IOException while serializing ping message.");
        return;
      }

      msgSender.send(msgBytes);

      try {
        Thread.sleep(pingIntervalMillis);
      } catch (InterruptedException e) {
        logger.error("Interrupted while waiting timeout to send next ping message, exiting");
        return;
      }
    }

    logger.info("Stopped pingThread");
  }

  public void handlePingResponse(PingMessage pm) {
    // nonce has to match with our nonce
    if (!Arrays.equals(lastPingNonce, pm.getNonce())) {
      logger.error(
          "Nonce mismatch with sent ping and received pong message, stop processing pong message");
    }

    // Nonce matches -> we now calculate the ping
    long roundTripNanos = System.nanoTime() - lastPingNanos;
    long roundTripMillis = TimeUnit.MILLISECONDS.convert(roundTripNanos, TimeUnit.NANOSECONDS);

    long currentEwma = ewmaMillis.get();
    if (currentEwma == -1) {
      ewmaMillis.set(roundTripMillis);
    } else {
      long newEwma = (long) ((ewmaAlpha * roundTripMillis) + ((1.0 - ewmaAlpha) * currentEwma));
      ewmaMillis.set(newEwma);

      logger.debug("Updated ewma fron {} ms to {} ms", currentEwma, newEwma);
    }
  }

  public static byte[] generateSecureNonce(int lengthBytes) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] nonceBytes = new byte[lengthBytes];
    secureRandom.nextBytes(nonceBytes);
    return nonceBytes;
  }

  public long getCurrentPingMillis() {
    return this.ewmaMillis.get();
  }
}
