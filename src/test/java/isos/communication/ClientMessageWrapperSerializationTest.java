package isos.communication;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientMessageWrapperSerializationTest {

  @Test
  public void testEncodingDecoding() throws Exception {
    int sender = 42;
    long clientSequence = 1234;
    String payloadString = "Hello, world!";
    byte[] payload = payloadString.getBytes();

    ClientMessageWrapper original =
        new ClientMessageWrapper(sender, clientSequence, payload);

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
    assertEquals(clientSequence, decoded.getClientSequence());
    assertArrayEquals(payload, decoded.getPayload());
    assertEquals(payloadString, new String(decoded.getPayload()));
  }
}
