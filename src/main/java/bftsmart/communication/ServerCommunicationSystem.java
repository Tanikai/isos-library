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

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.CommunicationSystemServerSideFactory;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.communication.server.ServersCommunicationLayer;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.messages.TOMMessage;

import isos.utils.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level thread that manages Replica-Replica and Replica-Client communication.
 * Client Comms: CommunicationSystemServerSide
 * Replica Comms: ServersCommunicationLayer
 *
 * @author alysson
 */
public class ServerCommunicationSystem extends Thread {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private boolean doWork = true;
  public final long MESSAGE_WAIT_TIME = 100;

  private final ConfigurationManager configManager;

  // *** Replica-Replica (R-R) COMMUNICATION *******************************************************
  /** This queue contains messages sent from other replicas. It is passed to serversConn. */
  private final LinkedBlockingQueue<SystemMessage> inQueue;

  /** This class handles connections and communication with other replicas. */
  private final ServersCommunicationLayer serversConn;

  /** Callback class for received messages from replicas. */
  private final MessageHandler msgHandler;

  /**
   * View Controller
   */
  private final ServerViewController viewController = new ServerViewController();


  // *** Client-Replica (C-R) COMMUNICATION ********************************************************
  /** This class handles the messages received from clients. */
  private CommunicationSystemServerSide clientsConn;

  /**
   * Creates a new instance of ServerCommunicationSystem.
   *
   * @param configManager Provides configuration.
   * @param msgHandler Object that handles messages from other replicas. For client messages, see ServerCommunicationSystem.setRequestReceiver
   * @throws Exception TODO Kai: Constructor shouldn't throw exception (?)
   */
  public ServerCommunicationSystem(ConfigurationManager configManager, MessageHandler msgHandler)
      throws Exception {
    super("SCommS");

    this.configManager = configManager;
    this.msgHandler = Objects.requireNonNullElseGet(msgHandler, TOMHandler::new);

    inQueue = new LinkedBlockingQueue<>(configManager.getStaticConf().getInQueueSize());

    serversConn = new ServersCommunicationLayer(configManager, inQueue);

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

  /**
   * Retry to establish connections to replicas that are not connected yet.
   */
  public void waitUntilViewConnected() {
    serversConn.waitUntilViewConnected();
  }

  /**
   * Sets the request receiver, which handles incoming messages from clients
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
   * @param sm the message to be sent
   * @deprecated Use explicit clientSend / replicaSend instead
   */
  public void send(int[] targets, SystemMessage sm) {
    if (sm instanceof TOMMessage) {
      clientsConn.send(targets, (TOMMessage) sm, false);
    } else {
      logger.debug("--> sending message from: {} -> {}", sm.getSender(), targets);
      serversConn.send(targets, sm, true);
    }
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
