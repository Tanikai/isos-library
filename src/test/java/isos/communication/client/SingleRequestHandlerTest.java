package isos.communication.client;

import isos.communication.ClientMessageWrapper;
import isos.utils.ReplicaId;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SingleRequestHandlerTest {

  private static final int CLIENT_ID = 1;
  private static final int SESSION = 1;
  private static final int SEQ_ID = 1;
  private static final long TIMEOUT_SECONDS = 2;

  private static ClientMessageWrapper makeReply(int sender, byte[] payload) {
    return new ClientMessageWrapper(sender, SESSION, SEQ_ID, payload);
  }

  private static Comparator<ClientMessageWrapper> byteArrayComparator() {
    return (o1, o2) -> Arrays.equals(o1.getPayload(), o2.getPayload()) ? 0 : -1;
  }

  private static ReplyExtractor<byte[]> byteArrayExtractor() {
    return (replies) -> replies.get(0).getPayload();
  }

  @Test
  void testHappyPathQuorumReached() throws Exception {
    int quorum = 2;
    List<ReplicaId> replicas = List.of(new ReplicaId(0), new ReplicaId(1), new ReplicaId(2));
    SingleRequestHandler<byte[]> handler =
        new SingleRequestHandler<>(
            CLIENT_ID,
            SESSION,
            SEQ_ID,
            TIMEOUT_SECONDS,
            replicas,
            quorum,
            byteArrayComparator(),
            byteArrayExtractor());

    byte[] payload = new byte[] {1, 2, 3};
    Thread sender =
        new Thread(
            () -> {
              try {
                handler.processReply(makeReply(0, payload));
                Thread.sleep(200); // simulate delay
                handler.processReply(makeReply(1, payload));
              } catch (Exception ignored) {
              }
            });
    sender.start();
    byte[] result = handler.getResponse().get();
    assertArrayEquals(payload, result);
    sender.join();
  }

  @Test
  void testTimeoutNotEnoughReplies() {
    int quorum = 3;
    List<ReplicaId> replicas = List.of(new ReplicaId(0), new ReplicaId(1), new ReplicaId(2));
    SingleRequestHandler<byte[]> handler =
        new SingleRequestHandler<>(
            CLIENT_ID,
            SESSION,
            SEQ_ID,
            TIMEOUT_SECONDS,
            replicas,
            quorum,
            byteArrayComparator(),
            byteArrayExtractor());

    byte[] payload = new byte[] {1, 2, 3};
    Thread sender =
        new Thread(
            () -> {
              try {
                handler.processReply(makeReply(0, payload));
                // Only one reply, not enough for quorum
              } catch (Exception ignored) {
              }
            });
    sender.start();
    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> handler.getResponse().get(TIMEOUT_SECONDS + 1, TimeUnit.SECONDS));
    assertTrue(ex.getCause() instanceof TimeoutException);
    try {
      sender.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail("Test thread was interrupted");
    }
  }

  @Test
  void testQuorumImpossible() throws Exception {
    int quorum = 2;
    List<ReplicaId> replicas = List.of(new ReplicaId(0), new ReplicaId(1), new ReplicaId(2));
    SingleRequestHandler<byte[]> handler =
        new SingleRequestHandler<>(
            CLIENT_ID,
            SESSION,
            SEQ_ID,
            TIMEOUT_SECONDS,
            replicas,
            quorum,
            byteArrayComparator(),
            byteArrayExtractor());

    byte[] payload1 = new byte[] {1, 2, 3};
    byte[] payload2 = new byte[] {4, 5, 6};
    byte[] payload3 = new byte[] {7, 8, 9};
    Thread sender =
        new Thread(
            () -> {
              try {
                handler.processReply(makeReply(0, payload1));
                handler.processReply(makeReply(1, payload2));
                handler.processReply(makeReply(2, payload3));
              } catch (Exception ignored) {
              }
            });
    sender.start();
    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> handler.getResponse().get(TIMEOUT_SECONDS + 1, TimeUnit.SECONDS));
    System.out.println("exception " + ex.getMessage());
    assertTrue(ex.getCause() instanceof QuorumNotReachedException);
    sender.join();
  }

  @Test
  void testReplyExtractionOrder() throws Exception {
    int quorum = 2;
    List<ReplicaId> replicas = List.of(new ReplicaId(0), new ReplicaId(1), new ReplicaId(2));
    SingleRequestHandler<byte[]> handler =
        new SingleRequestHandler<>(
            CLIENT_ID,
            SESSION,
            SEQ_ID,
            TIMEOUT_SECONDS,
            replicas,
            quorum,
            byteArrayComparator(),
            byteArrayExtractor());

    byte[] payloadA = new byte[] {9, 9, 9};
    byte[] payloadB = new byte[] {8, 8, 8};
    Thread sender =
        new Thread(
            () -> {
              try {
                handler.processReply(makeReply(1, payloadB));
                handler.processReply(makeReply(0, payloadA));
                handler.processReply(makeReply(2, payloadA));
              } catch (Exception ignored) {
              }
            });
    sender.start();
    byte[] result = handler.getResponse().get();
    assertArrayEquals(payloadA, result);
    sender.join();
  }
}
