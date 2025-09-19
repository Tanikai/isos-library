package isos.benchmark.kvstore;

import isos.communication.client.QuorumNotReachedException;

import java.io.Console;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class KVStoreClientInteractive {

  /**
   * Starts an interactive client session that accesses the KV store replicas.
   *
   * @see bftsmart.demo.map.MapInteractiveClient
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: isos.benchmark.kvstore.KVStoreClientInteractive <client id>");
      return;
    }

    int clientId = Integer.parseInt(args[0]);
    KVStoreClient<String, String> client = new KVStoreClient<>(clientId);
    Console console = System.console();

    boolean exit = false;
    String key, value, result;

    while (!exit) {
      System.out.println("Select an option:");
      System.out.println("1 - Put value");
      System.out.println("2 - Get value");
      System.out.println("9 - exit");

      int cmd = Integer.parseInt(console.readLine("Option:"));
      try {
        switch (cmd) {
          case 1 -> {
            System.out.println("Selected: Put value");
            key = console.readLine("Enter key:");
            value = console.readLine("Enter value:");
            client.put(key, value);
            System.out.println("Success");
          }
          case 2 -> {
            System.out.println("Selected: Get value");
            key = console.readLine("Enter key:");
            result = client.get(key);
            if (result == null) {
              System.out.println("No value present");
            } else {
              System.out.println("Value: " + result);
            }
          }
          case 9 -> {
            client.close();
            exit = true;
          }
        }
      } catch (IOException e) {
        System.out.println("IOException:" + e);
      } catch (TimeoutException e) {
        System.out.println("Timeout reached for request. Please retry.");
      } catch (QuorumNotReachedException e) {
        System.out.println(
            "Did not receive a quorum of identical responses from replicas. Please retry.");
      } catch (ClassNotFoundException e) {
        System.out.println("ClassNotFoundException: " + e);
      } catch (Exception e) {
        System.out.println("Exception: " + e);
      }
    }

    System.out.println("Exited");
  }
}
