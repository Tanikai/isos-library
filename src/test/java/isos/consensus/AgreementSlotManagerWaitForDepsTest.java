package isos.consensus;

import static org.junit.jupiter.api.Assertions.*;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.consensus.model.TimeoutConfiguration;
import isos.message.replica.ISOSMessageWrapper;
import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepProposeWithRequest;
import isos.utils.ReplicaId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgreementSlotManagerWaitForDepsTest {

  private final TimeoutConfiguration timeoutConfig = new TimeoutConfiguration(1000);

  @Test
  void testWaitForDepsUnblocksWhenAllDepsCompleted() throws Exception {
    int numDeps = 3;
    var ownReplicaId = new ReplicaId(2);
    var otherReplicaIds = new ReplicaId[] {new ReplicaId(0), new ReplicaId(1)};

    AgreementSlotManager manager =
        new AgreementSlotManager(ownReplicaId, timeoutConfig, otherReplicaIds, 2);

    // We are waiting for the first agreement slot of each of the 3 replicas
    Set<SequenceNumber> waitDepSet = new HashSet<>();
    for (int i = 0; i < numDeps; i++) {
      SequenceNumber seq = new SequenceNumber(i, 0);
      waitDepSet.add(seq);
      manager.createSequenceNumberEntry(seq);
    }

    CountDownLatch waitStarted = new CountDownLatch(1);
    Thread waiter =
        new Thread(
            () -> {
              waitStarted.countDown();
              try {
                manager.waitForDeps(waitDepSet);
                System.out.println("Wait is done");
              } catch (InterruptedException e) {
                fail("waitForDeps interrupted");
              }
            });
    waiter.start();
    assertTrue(waitStarted.await(1, TimeUnit.SECONDS), "waitForDeps should start");
    Thread.sleep(1000); // ensure waiter is blocking
    assertTrue(waiter.isAlive(), "waitForDeps should be blocking before deps complete");

    // Complete each dependency by passing a DepProposeMessage with matching SequenceNumber
    for (SequenceNumber seq : waitDepSet) {
      DepProposeMessage msg =
          new DepProposeMessage(
              seq, seq.replicaIdRec(), "hash123", new DependencySet(), new HashSet<>());
      var wrapper = new ISOSMessageWrapper(new DepProposeWithRequest(msg, null), seq.replicaId());
      manager.processData(wrapper);
    }

    waiter.join(2000);
    assertFalse(waiter.isAlive(), "waitForDeps should unblock after all deps complete");
  }
}
