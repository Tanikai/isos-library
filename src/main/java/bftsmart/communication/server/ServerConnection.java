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
package bftsmart.communication.server;

import bftsmart.communication.SystemMessage;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class represents a connection with other server and manages sending and receiving messages.
 * ServerConnections are created by ServerCommunicationLayer.
 *
 * @author alysson
 */
public class ServerConnection {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static final long POLL_TIME = 5000;
  private final ConfigurationManager configManager;
  private SSLSocket socket;
  private DataOutputStream socketOutStream = null;
  private DataInputStream socketInStream = null;
  private final int remoteId;

  // Sender
  private SenderThread msgSender;

  // Receiver

  private final boolean useSenderThread;
  protected final LinkedBlockingQueue<byte[]> outQueue;
  private final LinkedBlockingQueue<SystemMessage> inQueue;

  private final Lock connectLock = new ReentrantLock();

  /** Only used when there is no sender Thread */
  private Lock sendLock;

  private boolean doWork = true;
  private ReceiverThread msgReceiver;

  private SecretKey secretKey = null;

  /** Tulio A. Ribeiro TLS vars. */
  private KeyStore ks = null;

  private FileInputStream fis = null;
  private SSLSocketFactory socketFactory;
  private static final String SECRET = "MySeCreT_2hMOygBwY";

  public ServerConnection(
      ConfigurationManager configManager,
      SSLSocket socket,
      int remoteId,
      LinkedBlockingQueue<SystemMessage> inQueue) {

    this.configManager = configManager;
    this.socket = socket;
    this.remoteId = remoteId;
    this.inQueue = inQueue;
    this.outQueue = new LinkedBlockingQueue<>(this.configManager.getStaticConf().getOutQueueSize());

    logger.info(
        "Create Serverconnection {} -> {}",
        this.configManager.getStaticConf().getProcessId(),
        remoteId);

    reconnect(socket);

    // ******* EDUARDO BEGIN **************//
    this.useSenderThread = this.configManager.getStaticConf().isUseSenderThread();

    if (useSenderThread && (this.configManager.getStaticConf().getTTPId() != remoteId)) {
      this.msgSender = new SenderThread();
      this.msgSender.start();
    } else {
      sendLock = new ReentrantLock();
    }

    // TODO Kai: Is TTP relevant for ISOS or not?

    //    if (!this.controller.getStaticConf().isTheTTP()) {
    //      if (this.controller.getStaticConf().getTTPId() == remoteId) {
    //        // Uma thread "diferente" para as msgs recebidas da TTP
    //        new TTPReceiverThread(replica).start();
    //      } else {
    //        new ReceiverThread().start();
    //      }
    //    }

    // New Version
    if (!this.configManager.getStaticConf().isTheTTP()) {
      this.msgReceiver = new ReceiverThread();
      this.msgReceiver.start();
    }
    // ******* EDUARDO END **************//
  }

  /**
   * @author Tulio A. Ribeiro.
   * @return SecretKey
   */
  public SecretKey getSecretKey() {
    if (secretKey != null) return secretKey;

    generateSecretKey();
    return secretKey;
  }

  /** Stop message sending and reception. */
  public void shutdown() {
    logger.debug("SHUTDOWN for {}", remoteId);

    doWork = false;
    closeSocket();
  }

  /** Used to send packets to the remote server. */
  public final void send(byte[] data) throws InterruptedException {
    // should send be blocking / throw error if disconnected?
    if (useSenderThread) {
      //      logger.info("Send with senderThread");
      // only enqueue messages if there queue is not full
      if (!outQueue.offer(data)) {
        logger.warn("Out queue for {} full (message discarded).", remoteId);
      }
    } else {
      logger.info("Send with lock");
      sendLock.lock();
      sendBytes(data);
      sendLock.unlock();
    }
  }

  /**
   * Converts an integer to a bytearray of length 4.
   *
   * @param value (4-byte) Integer to convert
   * @return Byte Array of length 4
   */
  public static byte[] intToByteArray(int value) {
    return new byte[] {
      (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
    };
  }

  /**
   * Builds the data of the message that is sent to other replicas.
   *
   * @param payload Actual payload for the recipient
   * @return Message that is ready for sending to recipient
   */
  public static byte[] buildMessage(byte[] payload) {
    int msgLength = payload.length;
    // TODO Kai: why is length 5 used here?
    byte[] data = new byte[5 + msgLength]; // without MAC

    // Bytes 0-3: Message Length (including message length int and null byte)
    System.arraycopy(ServerConnection.intToByteArray(msgLength), 0, data, 0, 4);
    // Bytes 4-n+4: Payload (with n as payload length)
    System.arraycopy(payload, 0, data, 4, msgLength);
    // Byte n+4+1: Nullbyte as Message Terminator (?)
    System.arraycopy(new byte[] {(byte) 0}, 0, data, 4 + msgLength, 1);
    return data;
  }

  /**
   * Try to send a message through the socket. If some problem is detected, a reconnection is done.
   */
  private void sendBytes(byte[] messageData) {
    boolean abort = false;
    do {
      if (abort) return; // if there is a need to reconnect, abort this method
      if (socket == null || socketOutStream == null) {
        logger.info("SendBytes: Socket is null, or outStream is null");
        waitAndConnect();
        abort = true;
        return;
      }

      // We have a working outStream / connection, which allows us to send the data
      try {
        // do an extra copy of the data to be sent, but on a single out stream write
        byte[] data = ServerConnection.buildMessage(messageData);
        socketOutStream.write(data);
      } catch (IOException ex) {
        logger.info("IO Exception while sendBytes: {}", ex.getMessage());
        closeSocket();
        waitAndConnect();
        abort = true;
      }

    } while (doWork);
  }

  /**
   * @return return true if a process shall connect to the remote process, false otherwise
   * @author Eduardo
   */
  private boolean isToConnect() {
    // the node with higher ID starts the connection
    return this.configManager.getStaticConf().getProcessId() > remoteId;
  }

  /**
   * (Re-)establish connection between peers.
   *
   * @param newSocket null to create new connection, non-null if connection of other replica was
   *     accepted (only used if processId is less than remoteId)
   */
  protected void reconnect(SSLSocket newSocket) {
    if (socket != null && socket.isConnected()) {
      //      logger.info("Reconnect called, but already connected");
      return; // do nothing if current socket is already connected
    }

    // else, reconnect
    try {
      connectLock.lock();

      if (isToConnect()) { // if I should connect, create a new connection
        logger.info(
            "I (Replica {}) should connect to Replica {}",
            this.configManager.getStaticConf().getProcessId(),
            remoteId);
        ssltlsCreateConnection();
      } else {
        logger.info("Remote has higher process ID, use Socket from received connection request.");
        // Save socket from accepted connection
        socket = newSocket;

        if (socket == null) {
          logger.warn(
              "Connection to remote should be established by {}, but newSocket is null",
              this.remoteId);
        }
      }

      // After establishing the connection (independent from who initiated), create an Output- and
      // InputStream
      if (socket != null) {
        try {
          socketOutStream = new DataOutputStream(socket.getOutputStream());
          socketInStream = new DataInputStream(socket.getInputStream());

          // authKey = null;
          // authenticateAndEstablishAuthKey();
        } catch (IOException ex) {
          logger.error("Failed to authenticate to replica", ex);
        }
      }
    } finally {
      // always unlock connectLock
      connectLock.unlock();
    }
  }

  private void closeSocket() {
    connectLock.lock();

    if (socket != null) {
      logger.info(
          "Close Socket {} -> {}",
          this.configManager.getStaticConf().getProcessId(),
          this.remoteId);
      try {
        socketOutStream.flush();
        socket.close();
      } catch (IOException ex) {
        logger.debug("Error closing socket to {}", remoteId);
      } catch (NullPointerException npe) {
        logger.debug("Socket already closed");
      }

      socket = null;
      socketOutStream = null;
      socketInStream = null;
    }

    connectLock.unlock();
  }

  /** Waits 1s and then tries to reconnect. */
  private void waitAndConnect() {
    if (doWork) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ie) {
        logger.error("Failed to sleep", ie);
      }

      outQueue.clear();
      reconnect(null);
    }
  }

  /** Thread used to send packets to the remote server. */
  private class SenderThread extends Thread {

    public SenderThread() {
      super("Sender for " + remoteId);
    }

    @Override
    public void run() {
      byte[] data = null;

      while (doWork) {
        // get a message to be sent
        try {
          data = outQueue.poll(POLL_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
          logger.error("Failed to poll message from outQueue", ex);
        }

        if (data != null) {
          sendBytes(data);
        }
      }

      logger.debug("Sender for {} stopped!", remoteId);
    }
  }

  /** Thread used to receive packets from the remote server. */
  protected class ReceiverThread extends Thread {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ReceiverThread() {
      super("Receiver for " + remoteId);
      logger.info("create receiver for {}", remoteId);
    }

    @Override
    public void run() {

      while (doWork) {
        // if socket of connection is null
        if (socket == null) {
          logger.info("ReceiverThread: socket is null, reconnecting");
          waitAndConnect();
          continue;
        }

        if (socketInStream == null) {
          logger.info("ReceiverThread: inStream is null, reconnecting");
          waitAndConnect();
          continue;
        }

        try {
          // read data length
          // FIXME Kai: possible DoS attack by using really long messages, thus reserving large
          // arrays?

          // if there are disconnection issues, does the socketInStream get renewed?
          int dataLength = socketInStream.readInt();
          byte[] data = new byte[dataLength];

          // read data
          int read = 0;
          do {
            read += socketInStream.read(data, read, dataLength - read);
          } while (read < dataLength);

          byte hasMAC = socketInStream.readByte();

          logger.trace("Read: {}, HasMAC: {}", read, hasMAC);

          SystemMessage sm =
              (SystemMessage) (new ObjectInputStream(new ByteArrayInputStream(data)).readObject());

          // The verification it is done for the SSL/TLS protocol.
          sm.authenticated = true;

          if (sm.getSender() == remoteId) {
            if (!inQueue.offer(sm)) {
              logger.warn("Inqueue full (message from " + remoteId + " discarded).");
            } /* else {
              	logger.trace("Message: {} queued, remoteId: {}", sm.toString(), sm.getSender());
              }*/
          } else {
            logger.info(
                "ReceiverThread: Mismatch of Senders: {} (Message) - {} (Connection) (Actual message: {})",
                sm.getSender(),
                remoteId,
                sm);
          }
        } catch (ClassNotFoundException ex) {
          logger.info("Invalid message received. Ignoring!");
        } catch (IOException ex) {
          if (doWork) {
            logger.info("Closing socket and reconnecting: {}", ex.getMessage());
            closeSocket();
            waitAndConnect();
          }
        } catch (Exception ex) {
          logger.info("Processing message failed. Ignoring!");
        }
      }
    }
  }

  // ******* EDUARDO BEGIN: special thread for receiving messages indicating the entrance into the
  // system, coming from the TTP **************//
  // Simply pass the messages to the replica, indicating its entry into the system
  // TODO: Ask eduardo why a new thread is needed!!!
  // TODO 2: Remove all duplicated code

  // ******* EDUARDO END **************//

  /**
   * Deal with the creation of SSL/TLS connection.
   *
   * @author Tulio A. Ribeiro
   */
  public void ssltlsCreateConnection() {
    generateSecretKey();

    String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
    try {
      fis =
          new FileInputStream(
              "config/keysSSL_TLS/" + this.configManager.getStaticConf().getSSLTLSKeyStore());
      ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(fis, SECRET.toCharArray());
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
      logger.error("SSL connection error.", e);
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          logger.error("IO error.", e);
        }
      }
    }
    try {
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
      kmf.init(ks, SECRET.toCharArray());

      TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
      trustMgrFactory.init(ks);
      SSLContext context =
          SSLContext.getInstance(this.configManager.getStaticConf().getSSLTLSProtocolVersion());
      context.init(kmf.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());
      socketFactory = context.getSocketFactory();

    } catch (KeyStoreException
        | NoSuchAlgorithmException
        | UnrecoverableKeyException
        | KeyManagementException e) {
      logger.error("SSL connection error.", e);
    }
    // Create the connection.
    try {
      this.socket =
          (SSLSocket)
              socketFactory.createSocket(
                  this.configManager.getStaticConf().getHost(remoteId),
                  this.configManager.getStaticConf().getServerToServerPort(remoteId));
      this.socket.setKeepAlive(true);
      this.socket.setTcpNoDelay(true);
      this.socket.setEnabledCipherSuites(this.configManager.getStaticConf().getEnabledCiphers());

      this.socket.addHandshakeCompletedListener(
          new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
              logger.info(
                  "SSL/TLS handshake complete!, Id:{}" + "  ## CipherSuite: {}.",
                  remoteId,
                  event.getCipherSuite());
            }
          });

      this.socket.startHandshake();

      ServersCommunicationLayer.setSSLSocketOptions(this.socket);
      new DataOutputStream(this.socket.getOutputStream())
          .writeInt(this.configManager.getStaticConf().getProcessId());

    } catch (SocketException | UnknownHostException e) {
      logger.error(
          "Failed connection from replica {} -> replica {}: {}",
          this.configManager.getStaticConf().getProcessId(),
          this.remoteId,
          e.getMessage());

    } catch (IOException e) {
      logger.error("IO error.", e);
    }
  }

  private void generateSecretKey() {
    SecretKeyFactory fac;
    PBEKeySpec spec;
    try {
      fac = TOMUtil.getSecretFactory();
      spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
      this.secretKey = fac.generateSecret(spec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      logger.error("Algorithm error.", e);
    }
  }
}
