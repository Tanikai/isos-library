/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated
 * in the @author tags
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.communication;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.CommunicationSystemServerSideFactory;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.communication.server.ServersCommunicationLayer;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.core.messages.TOMMessage;
import isos.communication.MessageSender;
import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level thread that manages Replica-Replica and Replica-Client communication. Client Comms:
 * CommunicationSystemServerSide Replica Comms: ServersCommunicationLayer
 *
 * @author alysson
 */
public class ServerCommunicationSystem extends Thread implements MessageSender {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private boolean doWork = true;
  public final long MESSAGE_WAIT_TIME = 100;

  private final ConfigurationManager configManager;

  // ***** General Concurrency *********************************************************************

  /**
   * This executor service is used to serialize messages and send them with a virtual thread, so
   * that the consensus engine can process further messages (instead of handling sending the msg)
   */
  // TODO Kai: Benchmarking?
  private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

  // ***** Replica-Replica (R-R) COMMUNICATION *****************************************************
  /** This queue contains messages sent from other replicas. It is passed to serversConn. */
  private final LinkedBlockingQueue<SystemMessage> inQueue;

  /** This class handles connections and communication with other replicas. */
  private final ServersCommunicationLayer serversConn;

  /** Callback class for received messages from replicas. */
  private final MessageHandler msgHandler;

  // ViewController
  //  private final ServerViewController viewController = new ServerViewController();

  // ***** Client-Replica (C-R) COMMUNICATION ******************************************************
  /** This class handles the messages received from clients. */
  private CommunicationSystemServerSide clientsConn;

  /**
   * Creates a new instance of ServerCommunicationSystem.
   *
   * @param configManager Provides configuration.
   * @param msgHandler Object that handles messages from other replicas. For client messages, see
   *     ServerCommunicationSystem.setRequestReceiver
   * @throws Exception TODO Kai: Constructor shouldn't throw exception (?)
   */
  public ServerCommunicationSystem(ConfigurationManager configManager, MessageHandler msgHandler)
      throws Exception {
    super("SCommS");

    this.configManager = configManager;
    this.msgHandler = Objects.requireNonNullElseGet(msgHandler, TOMHandler::new);

    inQueue = new LinkedBlockingQueue<>(configManager.getStaticConf().getInQueueSize());

    serversConn = new ServersCommunicationLayer(configManager, inQueue);
    serversConn.initialize();

    // ******* EDUARDO BEGIN **************//
    clientsConn =
        CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(this.configManager);
    // ******* EDUARDO END **************//
  }

  /** Thread method responsible for receiving messages sent by other replicas. */
  @Override
  public void run() {
    long count = 0;
    while (doWork) {
      try {
        if (count % 1000 == 0 && count > 0) {
          logger.debug("After {} messages, inQueue size={}", count, inQueue.size());
        }

        SystemMessage sm = inQueue.poll(MESSAGE_WAIT_TIME, TimeUnit.MILLISECONDS);

        if (sm != null) {
          logger.debug("<-- receiving, msg:{}", sm);
          msgHandler.processData(sm);
          count++;
        } else {
          msgHandler.verifyPending();
        }
      } catch (InterruptedException e) {
        logger.error("Error processing message", e);
      }
    }
    logger.info("Stop ExecutorService of ServerCommunicationSystem");
    this.sendExecutor.shutdown();
    logger.info("ServerCommunicationSystem stopped.");
  }

  /**
   * Establishes connections to pending connections to replicas from the new view.
   *
   * @author Eduardo
   */
  public void joinViewReceived() {
    serversConn.joinViewReceived();
  }

  /**
   * @author Eduardo
   */
  public void updateServersConnections() {
    this.serversConn.updateConnections();
    if (clientsConn == null) {
      clientsConn =
          CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(this.configManager);
    }
  }

  /** Retry to establish connections to replicas that are not connected yet. */
  public void waitUntilViewConnected() {
    serversConn.waitUntilViewConnected();
  }

  /**
   * Sets the request receiver, which handles incoming messages from clients
   *
   * @param requestReceiver
   */
  public void setRequestReceiver(RequestReceiver requestReceiver) {
    if (clientsConn == null) {
      clientsConn =
          CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(this.configManager);
    }
    clientsConn.setRequestReceiver(requestReceiver);
  }

  /**
   * Send a message to target processes. If the message is an instance of TOMMessage, it is sent to
   * the clients, otherwise it is set to the servers.
   *
   * @param targets the target receivers of the message
   * @param sm the message to be sent @Deprecated Use explicit clientSend / replicaSend instead
   */
  public void send(int[] targets, SystemMessage sm) {
    if (sm instanceof TOMMessage) {
      clientsConn.send(targets, (TOMMessage) sm, false);
    } else {
      logger.debug("--> sending message from: {} -> {}", sm.getSender(), targets);
      serversConn.send(targets, sm, true);
    }
  }

  /**
   * Thread-safe function to send a message to clients. Creates a new virtual thread to serialize
   * and send.
   *
   * @param targets
   * @param sm
   */
  public void sendToClient(int[] targets, SystemMessage sm) {
    throw new NotImplementedException();
    //    this.sendExecutor.submit(
    //        () -> {
    //          // FIXME Kai: be able to send messages that are not TOMMessages
    //          clientsConn.send(targets, (TOMMessage) sm, false);
    //        });
  }

  /**
   * Thread-safe function to send a message to replicas. Creates a new virtual thread to serialize
   * and send.
   *
   * @param targets
   * @param sm
   */
  public void sendToReplicas(ReplicaId[] targets, SystemMessage sm) {
    this.sendExecutor.submit(
        () -> {
          int[] targetsAsInt = Arrays.stream(targets).mapToInt(ReplicaId::value).toArray();
          serversConn.send(targetsAsInt, sm, true);
        });
  }

  /**
   * Thread-safe function to broadcast a message to replicas. Creates a new virtual thread so
   * serialize and send.
   *
   * @param includeSelf
   * @param sm
   */
  public void broadcastToReplicas(boolean includeSelf, SystemMessage sm) {
    List<ReplicaId> targets = this.serversConn.getAllConnectedReplicas(includeSelf);
    this.sendToReplicas(targets.toArray(ReplicaId[]::new), sm);
  }

  @Override
  public String toString() {
    return serversConn.toString();
  }

  public void shutdown() {
    logger.info("Shutting down communication layer");

    this.doWork = false;
    clientsConn.shutdown();
    serversConn.shutdown();
    try {
      serversConn.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public SecretKey getSecretKey(int id) {
    return serversConn.getSecretKey(id);
  }
}
