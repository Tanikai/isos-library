package isos.communication;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;

public class ClientMessageWrapperSerializationTest {

  @Test
  public void testEncodingDecoding() throws Exception {
    int sender = 42;
    int clientSession = 7;
    int clientSequence = 1234;
    String payloadString = "Hello, world!";
    byte[] payload = payloadString.getBytes();

    ClientMessageWrapper original =
        new ClientMessageWrapper(sender, clientSession, clientSequence, payload);

    // Serialize
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    original.writeExternal(oos);
    oos.flush();
    byte[] serialized = baos.toByteArray();

    // Deserialize
    ClientMessageWrapper decoded = new ClientMessageWrapper();
    ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
    ObjectInputStream ois = new ObjectInputStream(bais);
    decoded.readExternal(ois);

    assertEquals(sender, decoded.getSender());
    assertEquals(clientSession, decoded.getClientSession());
    assertEquals(clientSequence, decoded.getClientSequence());
    assertArrayEquals(payload, decoded.getPayload());
    assertEquals(payloadString, new String(decoded.getPayload()));
  }
}
