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
import java.net.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author alysson Tulio A. Ribeiro. Generate a KeyPair used by SSL/TLS connections. Note that
 *     keypass argument is equal to the variable SECRET.
 *     <p>The command generates the secret key.
 */
// ## Elliptic Curve
// $keytool -genkey -keyalg EC -alias bftsmartEC -keypass MySeCreT_2hMOygBwY -keystore ./ecKeyPair
// -dname "CN=BFT-SMaRT"
// $keytool -importkeystore -srckeystore ./ecKeyPair -destkeystore ./ecKeyPair -deststoretype pkcs12

// ## RSA
// $keytool -genkey -keyalg RSA -keysize 2048 -alias bftsmartRSA -keypass MySeCreT_2hMOygBwY
// -keystore ./RSA_KeyPair_2048.pkcs12 -dname "CN=BFT-SMaRT"
// $keytool -importkeystore -srckeystore ./RSA_KeyPair_2048.pkcs12 -destkeystore
// ./RSA_KeyPair_2048.pkcs12 -deststoretype pkcs12

/** Thread that connects to other replicas. */
public class ServersCommunicationLayer extends Thread {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  //  private final ServerViewController controller;
  private final ConfigurationManager configManager;
  private final LinkedBlockingQueue<SystemMessage> inQueue;
  private final HashMap<Integer, ServerConnection> connections = new HashMap<>();
  private ServerSocket serverSocket;
  private final int me;
  private boolean doWork = true;
  private final Lock connectionsLock = new ReentrantLock();
  private final ReentrantLock waitViewLock = new ReentrantLock();
  private final List<PendingConnection> pendingConn = new LinkedList<>();

  /** Tulio A. Ribeiro SSL / TLS. */
  private static final String SECRET = "MySeCreT_2hMOygBwY";

  private final SecretKey selfPwd;
  private final SSLServerSocket serverSocketSSLTLS;

  public ServersCommunicationLayer(
      ConfigurationManager configManager, LinkedBlockingQueue<SystemMessage> inQueue)
      throws Exception {

    this.configManager = configManager;
    this.inQueue = inQueue;
    this.me = configManager.getStaticConf().getProcessId();
    String ssltlsProtocolVersion = configManager.getStaticConf().getSSLTLSProtocolVersion();

    String myAddress;
    String confAddress = "";
    try {
      confAddress =
          configManager
              .getStaticConf()
              .getRemoteAddress(configManager.getStaticConf().getProcessId())
              .getAddress()
              .getHostAddress();
    } catch (Exception e) {
      // Now look what went wrong ...
      logger.debug(" ####### Debugging at setting up the Communication layer ");
      logger.debug(
          "my Id is {}, my remote Address is {}",
          configManager.getStaticConf().getProcessId(),
          configManager
              .getStaticConf()
              .getRemoteAddress(configManager.getStaticConf().getProcessId()));
    }

    if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {
      myAddress = InetAddress.getLoopbackAddress().getHostAddress();
    } else if (configManager.getStaticConf().getBindAddress().isEmpty()) {
      myAddress = InetAddress.getLocalHost().getHostAddress();
      // If the replica binds to the loopback address, clients will not be able to connect to
      // replicas.
      // To solve that issue, we bind to the address supplied in config/hosts.config instead.
      if (InetAddress.getByName(myAddress).isLoopbackAddress() && !myAddress.equals(confAddress)) {
        myAddress = confAddress;
      }
    } else {
      myAddress = configManager.getStaticConf().getBindAddress();
    }

    int myPort =
        configManager
            .getStaticConf()
            .getServerToServerPort(configManager.getStaticConf().getProcessId());

    KeyStore ks;
    try (FileInputStream fis =
        new FileInputStream(
            "config/keysSSL_TLS/" + configManager.getStaticConf().getSSLTLSKeyStore())) {
      ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(fis, SECRET.toCharArray());
    }

    String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
    kmf.init(ks, SECRET.toCharArray());

    TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
    trustMgrFactory.init(ks);

    SSLContext context = SSLContext.getInstance(ssltlsProtocolVersion);
    context.init(kmf.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());

    SSLServerSocketFactory serverSocketFactory = context.getServerSocketFactory();
    this.serverSocketSSLTLS =
        (SSLServerSocket)
            serverSocketFactory.createServerSocket(myPort, 100, InetAddress.getByName(myAddress));

    serverSocketSSLTLS.setEnabledCipherSuites(
        this.configManager.getStaticConf().getEnabledCiphers());

    String[] ciphers = serverSocketFactory.getSupportedCipherSuites();
    for (String cipher : ciphers) {
      logger.trace("Supported Cipher: {} ", cipher);
    }

    // serverSocketSSLTLS.setPerformancePreferences(0, 2, 1);
    // serverSocketSSLTLS.setSoTimeout(connectionTimeoutMsec);
    serverSocketSSLTLS.setEnableSessionCreation(true);
    serverSocketSSLTLS.setReuseAddress(true);
    serverSocketSSLTLS.setNeedClientAuth(true);
    serverSocketSSLTLS.setWantClientAuth(true);

    SecretKeyFactory fac = TOMUtil.getSecretFactory();
    PBEKeySpec spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
    selfPwd = fac.generateSecret(spec);

    // Try connecting if a member of the current view. Otherwise, wait until the Join has been
    // processed!
    // FIXME Kai: remove controller from server communication layer

    //    if (controller.isInCurrentView()) {
    //      int[] initialV = controller.getCurrentViewAcceptors();
    //      for (int j : initialV) {
    //        if (j != me) {
    //          getConnection(j);
    //        }
    //      }
    //    }

    start();
  }

  /**
   * Main loop of the thread that connects to other replicas. While it is running, it accepts new
   * connections and creates new ServerConnection objects to handle them.
   */
  @Override
  public void run() {
    while (doWork) {
      try {
        SSLSocket newSocket = (SSLSocket) serverSocketSSLTLS.accept();
        setSSLSocketOptions(newSocket);

        int remoteId = new DataInputStream(newSocket.getInputStream()).readInt();

        //        if (!this.controller.isInCurrentView()
        //            && (this.configManager.getStaticConf().getTTPId() != remoteId)) {
        //          waitViewLock.lock();
        //          pendingConn.add(new PendingConnection(newSocket, remoteId));
        //          waitViewLock.unlock();
        //        } else {
        logger.debug("Trying establish connection with Replica: {}", remoteId);
        acceptConnection(newSocket, remoteId);
        //        }

      } catch (SocketTimeoutException ex) {
        logger.trace("Server socket timed out, retrying");
      } catch (SSLHandshakeException sslex) {
        logger.error("SSL handshake failed", sslex);
      } catch (IOException ex) {
        logger.error("Problem during thread execution", ex);
      }
    }

    try {
      serverSocket.close();
    } catch (IOException ex) {
      logger.error("Failed to close server socket", ex);
    }

    logger.info("ServerCommunicationLayer stopped.");
  }

  public SecretKey getSecretKey(int id) {
    if (id == configManager.getStaticConf().getProcessId()) return selfPwd;
    else return connections.get(id).getSecretKey();
  }

  /**
   * Update the connections to other replicas. Call this method on startup or when the view changes.
   *
   * @author Eduardo, Kai
   */
  public void updateConnections() {
    connectionsLock.lock();
    try {
      // FIXME Kai: remove controller from server communication layer

      // TODO Kai: do we need to remove unused connections? can't we keep them alive and use a
      // getter
      // function to get all IDs for current

      //         // remove connections to replicas that are not in the current view
      //          Iterator<Integer> it = this.connections.keySet().iterator();
      //          List<Integer> toRemove = new LinkedList<>();
      //          while (it.hasNext()) {
      //            int rm = it.next();
      //            if (!this.controller.isCurrentViewMember(rm)) {
      //              toRemove.add(rm);
      //            }
      //          }
      //          for (Integer integer : toRemove) {
      //            this.connections.remove(integer).shutdown();
      //          }

      int[] newV = configManager.getCurrentViewIds();
      logger.info("Update view connections: {}", Arrays.toString(newV));
      for (int i : newV) {
        if (i != me) {
          logger.info("Connecting to replica: {}", i);
          getOrCreateConnection(i);
        }
      }
    } finally {
      connectionsLock.unlock();
    }
  }

  /**
   * Get the connection to a id. If the connection does not exist, it will be created.
   *
   * @param remoteId remote replica id.
   * @return Server Connection.
   * @author Eduardo
   */
  private ServerConnection getOrCreateConnection(int remoteId) {
    connectionsLock.lock();
    try {
      ServerConnection ret = this.connections.get(remoteId);
      if (ret == null) {
        ret = new ServerConnection(this.configManager, null, remoteId, this.inQueue);
        this.connections.put(remoteId, ret);
      }
      return ret;
    } finally {
      connectionsLock.unlock();
    }
  }

  /**
   * Retry connecting to the current view IDs until all connections are established. Should only be
   * used initially during startup when the replicas have to be started manually. After that,
   * **especially after view change**, connections should be updated with another method.
   */
  public void waitUntilViewConnected() {
    Set<Integer> targetViewIds = new HashSet<>();
    for (int i : this.configManager.getCurrentViewIds()) {
      if (i != me) targetViewIds.add(i); // connect to everyone else but self
    }
    while (!this.connections.keySet().containsAll(targetViewIds)) {
      logger.info("Not connected to initial replicas. Connecting...");
      logger.info("Current connections: {}", this.connections.keySet());
      logger.info("Target connections: {}", targetViewIds);
      updateConnections();
      // wait for a while before retrying
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ex) {
        logger.error("Interrupted while waiting for connections", ex);
      }
    }
    logger.info("Connected to initial replicas.");
  }

  public final void send(int[] targets, SystemMessage sm, boolean useMAC) {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
    try {
      new ObjectOutputStream(bOut).writeObject(sm);
    } catch (IOException ex) {
      logger.error("Failed to serialize message", ex);
    }

    byte[] data = bOut.toByteArray();

    // this shuffling is done to prevent the replica with the lowest ID/index from being always
    // the last one receiving the messages, which can result in that replica to become consistently
    // delayed in relation to the others.
    // Tulio A. Ribeiro
    Integer[] targetsShuffled = Arrays.stream(targets).boxed().toArray(Integer[]::new);
    Collections.shuffle(Arrays.asList(targetsShuffled), new Random(System.nanoTime()));

    for (int target : targetsShuffled) {
      try {
        if (target == me) {
          sm.authenticated = true;
          inQueue.put(sm);
          logger.debug("Queueing (delivering) my own message, me:{}", target);
        } else {
          logger.debug("Sending message from:{} -> to:{}.", me, target);
          getOrCreateConnection(target).send(data);
        }
      } catch (InterruptedException ex) {
        logger.error("Interruption while inserting message into inqueue", ex);
      }
    }
  }

  public void shutdown() {
    logger.info("Shutting down all replica sockets");

    doWork = false;

    connectionsLock.lock();
    for (var connection : this.connections.entrySet()) {
      connection.getValue().shutdown();
    }
    connectionsLock.unlock();
  }

  /**
   * Setup connections to other replicas in the current view.
   *
   * @author Eduardo
   */
  public void joinViewReceived() {
    waitViewLock.lock();
    for (PendingConnection pc : pendingConn) {
      try {
        acceptConnection(pc.s, pc.remoteId);
      } catch (Exception e) {
        logger.error("Failed to establish connection to {}", pc.remoteId, e);
      }
    }

    pendingConn.clear();

    waitViewLock.unlock();
  }

  /**
   * Establish a new connection with a replica. Does not contain any logic and/or checks whether the
   * replica is in the current view or not. This has to be done at a higher abstraction level.
   *
   * @param newSocket the socket to be used for the connection
   * @param remoteId id of the replica
   * @throws IOException
   * @author Eduardo
   */
  private void acceptConnection(SSLSocket newSocket, int remoteId) throws IOException {
    connectionsLock.lock();
    if (this.connections.get(remoteId) == null) {
      // This must never happen!!!
      // first time that this connection is being established
      // System.out.println("THIS DOES NOT HAPPEN....."+remoteId);
      this.connections.put(
          remoteId, new ServerConnection(configManager, newSocket, remoteId, inQueue));
    } else {
      // reconnection
      logger.debug("Reconnecting with replica: {}", remoteId);
      this.connections.get(remoteId).reconnect(newSocket);
    }
    connectionsLock.unlock();
  }

  public static void setSSLSocketOptions(SSLSocket socket) {
    try {
      socket.setTcpNoDelay(true);
    } catch (SocketException ex) {
      LoggerFactory.getLogger(ServersCommunicationLayer.class)
          .error("Failed to set TCPNODELAY", ex);
    }
  }

  public static void setSocketOptions(Socket socket) {
    try {
      socket.setTcpNoDelay(true);
    } catch (SocketException ex) {
      LoggerFactory.getLogger(ServersCommunicationLayer.class)
          .error("Failed to set TCPNODELAY", ex);
    }
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder("inQueue=" + inQueue.toString());
    // FIXME: define active servers without viewAcceptors

    Integer[] activeConnections = this.connections.keySet().toArray(Integer[]::new);
    for (int connId : activeConnections) {
      if (me != connId) {
        str.append(", connections[")
            .append(connId)
            .append("]: outQueue=")
            .append(getOrCreateConnection(connId).outQueue);
      }
    }
    return str.toString();
  }

  // ******* EDUARDO BEGIN: List entry that stores pending connections,
  // as a server may accept connections only after learning the current view,
  // i.e., after receiving the response to the join*************//
  // This is for avoiding that the server accepts from everywhere
  public static class PendingConnection {

    public SSLSocket s;
    public int remoteId;

    public PendingConnection(SSLSocket s, int remoteId) {
      this.s = s;
      this.remoteId = remoteId;
    }
  }

  // ******* EDUARDO END **************//
}
