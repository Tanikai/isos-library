package bftsmart.demo.messaging;

import bftsmart.tom.ServiceProxy;

import java.io.*;

public class MessagingClient {

  public static void main(String[] args) throws IOException {

    int processId = 1;
    int numberOps = 10;
    try (ServiceProxy proxy = new ServiceProxy(processId)) {
      for (int i = 0; i < numberOps; i++) {

        byte[] msg = ("Hello from client " + i).getBytes();
        // FIXME: currently there is no response from
        byte[] reply = proxy.invokeOrdered(msg);

        if (reply != null) {
          String response = new String(reply);
          System.out.println("Received response: " + response);
        }
      }
    }
  }
}
