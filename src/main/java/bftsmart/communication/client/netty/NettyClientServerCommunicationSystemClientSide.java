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
package bftsmart.communication.client.netty;

import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.communication.server.PingHandler;
import bftsmart.communication.server.PingMessage;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.TOMUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;
import isos.communication.ClientMessageWrapper;
import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class is an implementation of the ServerCommunicationSystemClientSide
 *
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemClientSide
    extends SimpleChannelInboundHandler<SystemMessage> implements CommunicationSystemClientSide {

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  private int clientId;
  protected ReplyReceiver trr;
  private ConfigurationManager configManager;

  /** This map contains the current active sessions to replicas. */
  private ConcurrentHashMap<Integer, NettyClientServerSession> replicaIdToSession;

  private ReentrantReadWriteLock replicaIdToSessionMapLock;
  private Signature signatureEngine;
  private boolean closed = false;

  private EventLoopGroup workerGroup;
  private SyncListener listener;

  // Ping
  private ScheduledExecutorService scheduledExecutor;
  private ScheduledFuture<?> pingTask;
  private List<ReplicaId> pingTargets;
  // Compared to the ping implementation in ServerConnection, we have only a single
  // NettyClientServerSystem that communicates with all replicas.
  private final ConcurrentHashMap<ReplicaId, byte[]> lastPingNonces;
  private final ConcurrentHashMap<ReplicaId, Long> lastPingNanos;
  private final ConcurrentHashMap<ReplicaId, Long> replicaPingMillis;
  private CountDownLatch remainingPings;
  private final int clientPingInterval;

  private SecretKeyFactory secretKeyFactory;

  /* Tulio Ribeiro */
  private static int tcpSendBufferSize = 8 * 1024 * 1024;
  private static int connectionTimeoutMsec = 40000; /* (40 seconds, timeout) */
  private PrivateKey privKey;
  /* end Tulio Ribeiro */

  // Used for a re-transmission of the last (pending) request in case of a re-connect to some
  // replica
  private ClientMessageWrapper pendingRequest;
  private boolean pendingRequestSign;

  public NettyClientServerCommunicationSystemClientSide(
      int clientId, ConfigurationManager configManager) {
    super();

    this.clientId = clientId;
    this.workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
    this.scheduledExecutor = new ScheduledThreadPoolExecutor(2);
    this.lastPingNonces = new ConcurrentHashMap<>();
    this.lastPingNanos = new ConcurrentHashMap<>();
    this.replicaPingMillis = new ConcurrentHashMap<>();

    this.configManager = configManager;
    this.clientPingInterval = this.configManager.getStaticConf().getClientPingIntervalMillis();

    /* Tulio Ribeiro */
    privKey = configManager.getStaticConf().getPrivateKey();
    try {
      this.secretKeyFactory = TOMUtil.getSecretFactory();

      this.listener = new SyncListener();
      this.replicaIdToSession = new ConcurrentHashMap<>();
      this.replicaIdToSessionMapLock = new ReentrantReadWriteLock();

      // FIXME: Currently no support for reconfiguration
      int[] currV = configManager.getStaticConf().getInitialView();

      for (int replicaId : currV) {
        try {
          ChannelFuture future = connectToReplica(replicaId, secretKeyFactory);

          logger.debug(
              "ClientID {}, connecting to replica {}, at address: {}",
              clientId,
              replicaId,
              configManager.getStaticConf().getRemoteAddress(replicaId));

          future.awaitUninterruptibly();

          if (!future.isSuccess()) {
            logger.error("Failed to connect to {}", replicaId);
            throw new RuntimeException(
                String.format(
                    "Not able to connect to replica %d with IP %s",
                    replicaId, configManager.getStaticConf().getRemoteAddress(replicaId)));
          }
        } catch (Exception ex) {
          logger.error("Failed to initialize MAC engine", ex);
        }
      }
    } catch (NoSuchAlgorithmException ex) {
      logger.error("Failed to initialize secret key factory", ex);
    }

    logger.debug(
        "Client {} is connected to initial view: {}", this.clientId, replicaIdToSession.keySet());
  }

  // TODO Kai: is this even needed for the communication channel? Can't this be solved somehow else?
  @Override
  public void updateConnections() {
    throw new NotImplementedException();
    //    int[] currV = controller.getCurrentViewProcesses();
    //    try {
    //      // open connections with new servers
    //      for (int replicaId : currV) {
    //        rl.readLock().lock();
    //        if (sessionClientToReplica.get(replicaId) == null) {
    //          rl.readLock().unlock();
    //          rl.writeLock().lock();
    //          try {
    //            ChannelFuture future = connectToReplica(replicaId, secretKeyFactory);
    //            logger.debug(
    //                "ClientID {}, updating connection to replica {}, at address: {}",
    //                clientId,
    //                replicaId,
    //                configManager.getStaticConf().getRemoteAddress(replicaId));
    //
    //            future.awaitUninterruptibly();
    //
    //            if (!future.isSuccess()) {
    //              logger.error("Impossible to connect to " + replicaId);
    //            }
    //
    //          } catch (InvalidKeyException | InvalidKeySpecException ex) {
    //            logger.error("Failed to initialize MAC engine", ex);
    //          }
    //          rl.writeLock().unlock();
    //        } else {
    //          rl.readLock().unlock();
    //        }
    //      }
    //    } catch (NoSuchAlgorithmException ex) {
    //      logger.error("Failed to initialzie secret key factory", ex);
    //    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    switch (cause) {
      case ClosedChannelException closedChannelException ->
          logger.error("Connection with replica closed.", cause);
      case ConnectException connectException ->
          logger.error("Impossible to connect to replica.", cause);
      case IOException ioException ->
          logger.error("Replica disconnected. Connection reset by peer.");
      case null, default -> logger.error("Replica disconnected.", cause);
    }
  }

  /**
   * Called when a channel to a replica receives a new message from the replica.
   *
   * @param ctx
   * @param sm
   * @throws Exception
   */
  @Override
  public void channelRead0(ChannelHandlerContext ctx, SystemMessage sm) throws Exception {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    if (sm instanceof ClientMessageWrapper wrapperMsg) {
      trr.replyReceived(wrapperMsg);
    } else if (sm instanceof PingMessage pingMsg) {
      this.handlePingMessage(ReplicaId.of(sm.getSender()), pingMsg);
    } else {
      logger.warn("Received unsupported SystemMessage, throwing away");
    }
  }

  private void handlePingMessage(ReplicaId sender, PingMessage pingMsg) {
    if (!pingMsg.isResponse()) {
      logger.warn("Received ping message that is not response, throwing away");
      return;
    }

    var lastPingNonce = this.lastPingNonces.get(sender);
    var lastPingNanos = this.lastPingNanos.get(sender);

    if (lastPingNonce == null) {
      logger.error("LastPingNonce of Replica {} does not exist", sender);
      return;
    }

    if (lastPingNanos == null) {
      logger.error("LastPingNanos of Replica {} does not exist", sender);
      return;
    }

    if (!Arrays.equals(lastPingNonce, pingMsg.getNonce())) {
      logger.error("Nonce mismatch with sent ping and received pong from {}", sender);
    }

    long roundTripNanos = System.nanoTime() - lastPingNanos;
    long roundTripMillis = TimeUnit.MILLISECONDS.convert(roundTripNanos, TimeUnit.NANOSECONDS);

    if (!this.replicaPingMillis.containsKey(sender)) {
      this.remainingPings.countDown();
      logger.debug("Remaining first pings to receive: {}", this.remainingPings.getCount());
    }

    this.replicaPingMillis.put(sender, roundTripMillis);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }
    logger.debug("Channel active");
  }

  @Override
  public void setPingTargets(List<ReplicaId> targets) throws InterruptedException {
    this.pingTargets = targets;
    // After starting the ping task, it waits until we have an initial ping for all replicas
    this.startPingTaskAndWait();
  }

  @Override
  public Map<ReplicaId, Long> getCurrentPings() {
    return Collections.unmodifiableMap(this.replicaPingMillis);
  }

  /** Starts the ping task */
  private void startPingTaskAndWait() throws InterruptedException {
    logger.debug("Try to start ping task");
    if (this.pingTask != null && !this.pingTask.isCancelled()) {
      logger.debug("Ping task is already started");
      return;
    }

    if (this.pingTargets == null) {
      logger.warn("Ping targets is null, cannot start ping task");
      return;
    }

    this.replicaPingMillis.clear();
    this.remainingPings = new CountDownLatch(this.pingTargets.size());
    this.pingTask =
        this.scheduledExecutor.scheduleAtFixedRate(
            () -> {
              if (this.pingTargets == null) {
                return;
              }
              var pingNonce = PingHandler.generateSecureNonce(16);
              var pingNanos = System.nanoTime();
              var msg = new PingMessage(this.clientId, pingNonce, false);
              this.send(false, this.pingTargets, msg, pingTargets.size());

              for (var target : this.pingTargets) {
                this.lastPingNanos.put(target, pingNanos);
                this.lastPingNonces.put(target, pingNonce);
              }
            },
            0,
            clientPingInterval,
            TimeUnit.MILLISECONDS);

    boolean latchReached =
        this.remainingPings.await(
            this.configManager.getStaticConf().getInitialWaitForPingsTimeoutMillis(),
            TimeUnit.MILLISECONDS);
    if (latchReached) {
      logger.debug(
          "Client {} received ping answers from all replicas. Start sending requests to replicas.",
          this.clientId);
    } else {
      logger.warn(
          "Only received pings from {} before reaching timeout. Remaining replicas might be missing replica->client connections, which prevents sending answers to clients.",
          this.replicaPingMillis.entrySet());
    }
  }

  private void stopPingTask() {
    this.pingTask.cancel(true);
    this.remainingPings = null;
    this.lastPingNonces.clear();
    this.lastPingNanos.clear();
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) {
    // Stop ping loop
    this.pingTask.cancel(false);

    scheduleReconnect(ctx, 10);
  }

  public void reconnect(final ChannelHandlerContext ctx) {
    replicaIdToSessionMapLock.writeLock().lock();

    ArrayList<NettyClientServerSession> sessions = new ArrayList<>(replicaIdToSession.values());
    for (NettyClientServerSession ncss : sessions) {
      if (ncss.getChannel() == ctx.channel()) {
        int replicaId = ncss.getReplicaId();
        try {

          if (configManager.getStaticConf().getRemoteAddress(replicaId) != null) {

            ChannelFuture future;
            try {
              future = connectToReplica(replicaId, secretKeyFactory);
              // Re-transmit a request after re-connection
              future.await();
              logger.info(
                  "Retransmitting message after a re-connect: "
                      + this.pendingRequest.getClientSequence());
              retransmitMessage(this.pendingRequest, replicaId, this.pendingRequestSign);
            } catch (InvalidKeyException | InvalidKeySpecException e) {
              // TODO Auto-generated catch block
              logger.error("Error in key.", e);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            logger.info(
                "ClientID {}, re-connection to replica {}, at address: {}",
                clientId,
                replicaId,
                configManager.getStaticConf().getRemoteAddress(replicaId));

          } else {
            // This cleans an old server from the session table
            removeClient(replicaId);
          }
        } catch (NoSuchAlgorithmException ex) {
          logger.error("Failed to reconnect to replica", ex);
        }
      }
    }

    replicaIdToSessionMapLock.writeLock().unlock();
  }

  @Override
  public void setReplyReceiver(ReplyReceiver trr) {
    this.trr = trr;
  }

  /**
   * Send a message from the client to a replica.
   *
   * @param sign Sign the message if true.
   * @param targets IDs of the replicas to send the message to.
   * @param sm Message to be sent.
   */
  @Override
  public void send(boolean sign, List<ReplicaId> targets, SystemMessage sm, int quorumSize) {
    if (sm instanceof ClientMessageWrapper wrapperMsg) {
      List<ReplicaId> shuffledTargets = new ArrayList<>(targets);
      Collections.shuffle(shuffledTargets, new Random());

      //      listener.waitForChannels(quorumSize); // wait for the previous transmission to
      // complete

      logger.debug(
          "Sending request from {} with sequence number {} to {}",
          wrapperMsg.getSender(),
          wrapperMsg.getClientSequence(),
          shuffledTargets);

      this.pendingRequest = wrapperMsg;
      this.pendingRequestSign = sign;

      if (wrapperMsg.serializedMessage == null) {
        try {
          ClientMessageWrapper.serializeMessage(wrapperMsg);
        } catch (IOException e) {
          logger.error("Could not serialize ClientMessageWrapper. Do not send message");
          return;
        }
      }

      // Logger.println("Sending message with "+sm.serializedMessage.length+" bytes of
      // content.");

      // produce signature
      if (sign && wrapperMsg.serializedMessageSignature == null) {
        wrapperMsg.serializedMessageSignature = signMessage(privKey, wrapperMsg.serializedMessage);
      }

      int sent = 0;

      for (ReplicaId target : shuffledTargets) {
        // This is done to avoid a race condition with the writeAndFlush method. Since the method
        // is asynchronous, each iteration of this loop could overwrite the destination of the
        // previous one
        wrapperMsg = wrapperMsg.clone();

        // TODO Kai: Why is the destination set here?
        wrapperMsg.destination = target.value();

        replicaIdToSessionMapLock.readLock().lock();
        Channel channel = replicaIdToSession.get(target.value()).getChannel();
        replicaIdToSessionMapLock.readLock().unlock();
        if (channel.isActive()) {
          wrapperMsg.signed = sign;
          ChannelFuture f = channel.writeAndFlush(sm);
          //          f.addListener(listener);
          sent++;
        } else {
          logger.debug("Channel to {} is not connected", target);
        }
      }

      // FIXME Kai: get F from somewhere else than controller
      //    if (targets.length > controller.getCurrentViewF() && sent < controller.getCurrentViewF()
      // +
      // 1) {
      //      // if less than f+1 servers are connected send an exception to the client
      //      throw new RuntimeException("Impossible to connect to servers!");
      //    }
      if (targets.size() == 1 && sent == 0) throw new RuntimeException("Server not connected");
    } else if (sm instanceof PingMessage pingMsg) {
      logger.debug("Send ping message (Current pings: {})", this.replicaPingMillis.entrySet());
      for (ReplicaId target : targets) {
        replicaIdToSessionMapLock.readLock().lock();
        Channel channel = replicaIdToSession.get(target.value()).getChannel();
        replicaIdToSessionMapLock.readLock().unlock();
        if (channel.isActive()) {
          ChannelFuture f = channel.writeAndFlush(pingMsg);
          //          f.addListener(listener);
        } else {
          logger.debug("Channel to {} is not connected", target);
        }
      }
    } else {
      logger.warn("Unsupported SystemMessage");
    }
  }

  /**
   * Serializes the message, signs the serialized message, and then stores the signature in the
   * field serializedMessageSignature.
   *
   * <p>TODO Kai: a sign method should not be responsible to serialize the message as well
   *
   * @param sm
   */
  public void sign(ClientMessageWrapper sm) {
    // serialize message
    try {
      ClientMessageWrapper.serializeMessage(sm);
    } catch (IOException ex) {
      logger.error("Failed to sign ClientMessageWrapper", ex);
    }
    // produce signature
    sm.serializedMessageSignature = signMessage(privKey, sm.serializedMessage);
  }

  public byte[] signMessage(PrivateKey key, byte[] message) {
    // long startTime = System.nanoTime();
    try {
      if (signatureEngine == null) {
        signatureEngine = TOMUtil.getSigEngine();
      }
      byte[] result = null;

      signatureEngine.initSign(key);
      signatureEngine.update(message);
      result = signatureEngine.sign();

      // st.store(System.nanoTime() - startTime);
      return result;
    } catch (Exception e) {
      logger.error("Failed to sign message", e);
      return null;
    }
  }

  @Override
  public void close() {
    this.stopPingTask();
    this.closed = true;
    // Iterator sessions = sessionClientToReplica.values().iterator();
    replicaIdToSessionMapLock.readLock().lock();
    ArrayList<NettyClientServerSession> sessions = new ArrayList<>(replicaIdToSession.values());
    replicaIdToSessionMapLock.readLock().unlock();
    for (NettyClientServerSession ncss : sessions) {
      Channel c = ncss.getChannel();
      closeChannelAndEventLoop(c);
    }
  }

  private ChannelInitializer<SocketChannel> getChannelInitializer()
      throws NoSuchAlgorithmException {

    final NettyClientPipelineFactory nettyClientPipelineFactory =
        new NettyClientPipelineFactory(
            this, replicaIdToSession, configManager, replicaIdToSessionMapLock);

    return new ChannelInitializer<>() {
      @Override
      public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(nettyClientPipelineFactory.getDecoder());
        ch.pipeline().addLast(nettyClientPipelineFactory.getEncoder());
        ch.pipeline().addLast(nettyClientPipelineFactory.getHandler());
      }
    };
  }

  @Override
  public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
    scheduleReconnect(ctx, 10);
  }

  private void closeChannelAndEventLoop(Channel c) {
    // once having an event in your handler (EchoServerHandler)
    // Close the current channel
    c.close();
    // Then close the parent channel (the one attached to the bind)
    if (c.parent() != null) {
      c.parent().close();
    }
    workerGroup.shutdownGracefully();
    scheduledExecutor.shutdown();
  }

  private void scheduleReconnect(final ChannelHandlerContext ctx, int time) {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    final EventLoop loop = ctx.channel().eventLoop();
    loop.schedule(() -> reconnect(ctx), time, TimeUnit.SECONDS);
  }

  /**
   * TODO Kai: is the SyncListener even required? Why do we have to wait for the previous operation
   * to complete?
   */
  private class SyncListener implements GenericFutureListener<ChannelFuture> {

    private int remainingFutures;

    private final Lock futureLock;
    private final Condition enoughCompleted;

    public SyncListener() {
      this.remainingFutures = 0;

      this.futureLock = new ReentrantLock();
      this.enoughCompleted = futureLock.newCondition();
    }

    @Override
    public void operationComplete(ChannelFuture f) {
      this.futureLock.lock();

      this.remainingFutures--;

      if (this.remainingFutures <= 0) {
        this.enoughCompleted.signalAll();
      }

      logger.debug("{} channel operations remaining to complete", this.remainingFutures);

      this.futureLock.unlock();
    }

    /**
     * If there are still remaining futures, wait until a set timeout expires. After the timeout
     * expires, set the remaining futures to the passed value.
     *
     * @param n number of channels to wait for
     */
    public void waitForChannels(int n) {
      this.futureLock.lock();
      if (this.remainingFutures > 0) {
        logger.info(
            "There are still {} channel operations pending, waiting to complete",
            this.remainingFutures);
        try {
          // timeout if a malicious replica refuses to acknowledge the operation as completed
          this.enoughCompleted.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
          logger.error("Interruption while waiting on condition", ex);
        }
      }

      logger.debug("All channel operations completed or timed out");

      this.remainingFutures = n;

      this.futureLock.unlock();
    }
  }

  /**
   * Connect to specific replica and returns the ChannelFuture. sessionClientToReplica is replaced
   * with the new connection. Removed redundant code.
   *
   * @param replicaId
   * @param fac
   * @return
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   * @throws InvalidKeyException
   * @author Tulio Ribeiro
   */
  public synchronized ChannelFuture connectToReplica(int replicaId, SecretKeyFactory fac)
      throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {

    String str = this.clientId + ":" + replicaId;
    PBEKeySpec spec = TOMUtil.generateKeySpec(str.toCharArray());
    SecretKey authKey = fac.generateSecret(spec);

    Bootstrap b = new Bootstrap();
    b.group(workerGroup);
    b.channel(NioSocketChannel.class);
    b.option(ChannelOption.SO_KEEPALIVE, true);
    b.option(ChannelOption.TCP_NODELAY, true);
    b.option(ChannelOption.SO_SNDBUF, tcpSendBufferSize);
    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMsec);
    b.handler(getChannelInitializer());

    ChannelFuture channelFuture =
        b.connect(configManager.getStaticConf().getRemoteAddress(replicaId));

    NettyClientServerSession ncss =
        new NettyClientServerSession(channelFuture.channel(), replicaId);
    replicaIdToSession.put(replicaId, ncss);

    return channelFuture;
  }

  public synchronized void removeClient(int clientId) {
    replicaIdToSession.remove(clientId);
  }

  /**
   * Re-transmits a pending request to a recovered replica after successful re-connection
   *
   * @param sm pending ClientMessageWrapper
   * @param replicaId recovered replica's id
   * @param sign if a signature should be added
   */
  private void retransmitMessage(ClientMessageWrapper sm, int replicaId, boolean sign) {
    // No pending request then abort;
    if (sm == null) {
      return;
    }
    logger.info(
        "Re-transmitting request from {} with sequence number {} to {}",
        sm.getSender(),
        sm.getClientSequence(),
        replicaId);

    // if message was not yet serialized, serialize it and cache it
    if (sm.serializedMessage == null) {
      try {
        ClientMessageWrapper.serializeMessage(sm);
      } catch (IOException e) {
        logger.error("Could not serialize ClientMessageWrapper. Do not send message");
        return;
      }
    }
    if (sign && sm.serializedMessageSignature == null) {
      sm.serializedMessageSignature = signMessage(privKey, sm.serializedMessage);
    }
    sm = sm.clone();
    sm.destination = replicaId;
    replicaIdToSessionMapLock.readLock().lock();
    Channel channel = replicaIdToSession.get(replicaId).getChannel();
    replicaIdToSessionMapLock.readLock().unlock();
    if (channel.isActive()) {
      sm.signed = sign;
      ChannelFuture f = channel.writeAndFlush(sm);
      //      f.addListener(listener);
    } else {
      logger.info("Channel to {} is not connected", replicaId);
    }
  }
}
