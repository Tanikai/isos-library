package isos.message;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OrderedClientRequestTest {

  @Test
  public void testCalculateHash() throws Exception {
    int clientId = 42;
    byte[] command = new byte[] {1, 2, 3, 4, 5};
    long clientLocalTimestamp = 123456789L;
    OrderedClientRequest req = new OrderedClientRequest(clientId, command, clientLocalTimestamp);

    String expected = "79566369a37d3ce2a049af8746fa613188094fbfe1aa28cff2c625fcf64c28b8";
    assertEquals(expected, req.calculateHash());
  }
}

