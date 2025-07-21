package isos.communication.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

public class CompletableFutureTest {

  @Test
  void CompleteAfterTimeoutTest() {

    var myfuture = new CompletableFuture<Integer>().orTimeout(500, TimeUnit.MILLISECONDS);

    Thread waiter =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    myfuture.get();
                  } catch (Exception e) {
                    assertInstanceOf(ExecutionException.class, e);
                    var execException = (ExecutionException) e;
                    assertInstanceOf(TimeoutException.class, execException.getCause());
                  }

                  myfuture.complete(5);

                  try {
                    myfuture.get();
                    // Exception should be thrown
                    fail();
                  } catch (Exception e) {
                    assertInstanceOf(TimeoutException.class, e.getCause());
                    System.out.println("Future is not completed due to timeout before compete");
                  }
                });
    try {
      waiter.join();
    } catch (Exception e) {
      fail();
    }
  }
}
