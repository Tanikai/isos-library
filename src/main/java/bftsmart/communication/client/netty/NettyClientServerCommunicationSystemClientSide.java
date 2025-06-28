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

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.TOMUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;
import isos.communication.ClientMessageWrapper;
import isos.utils.NotImplementedException;
import isos.utils.ReplicaId;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an implementation of the ServerCommunicationSystemClientSide
 *
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemClientSide
    extends SimpleChannelInboundHandler<ClientMessageWrapper>
    implements CommunicationSystemClientSide {

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  private int clientId;
  protected ReplyReceiver trr;
  private ConfigurationManager configManager;
  private ConcurrentHashMap<Integer, NettyClientServerSession> sessionClientToReplica =
      new ConcurrentHashMap<>();
  private ReentrantReadWriteLock rl;
  private Signature signatureEngine;
  private boolean closed = false;

  private EventLoopGroup workerGroup;
  private SyncListener listener;

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
    try {

      this.secretKeyFactory = TOMUtil.getSecretFactory();

      this.configManager = configManager;

      /* Tulio Ribeiro */
      privKey = configManager.getStaticConf().getPrivateKey();

      this.listener = new SyncListener();
      this.rl = new ReentrantReadWriteLock();

      // FIXME Kai: what about replicas not from the initial view?
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
          }

        } catch (NullPointerException ex) {
          // What is this??? This is not possible!!!
          logger.debug(
              "Should fix the problem, and I think it has no other implications :-), "
                  + "but we must make the servers store the view in a different place.");
        } catch (Exception ex) {
          logger.error("Failed to initialize MAC engine", ex);
        }
      }
    } catch (NoSuchAlgorithmException ex) {
      logger.error("Failed to initialize secret key factory", ex);
    }
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

  @Override
  public void channelRead0(ChannelHandlerContext ctx, ClientMessageWrapper sm) throws Exception {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }
    trr.replyReceived(sm);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }
    logger.debug("Channel active");
  }

  public void reconnect(final ChannelHandlerContext ctx) {

    rl.writeLock().lock();

    ArrayList<NettyClientServerSession> sessions =
        new ArrayList<NettyClientServerSession>(sessionClientToReplica.values());
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

    rl.writeLock().unlock();
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
  public void send(boolean sign, List<ReplicaId> targets, ClientMessageWrapper sm, int quorumSize) {
    int quorum = quorumSize;

    List<ReplicaId> shuffledTargets = new ArrayList<>(targets);
    Collections.shuffle(shuffledTargets, new Random());

    listener.waitForChannels(quorum); // wait for the previous transmission to complete

    logger.debug(
        "Sending request from {} with sequence number {} to {}",
        sm.getSender(),
        sm.getClientSequence(),
        shuffledTargets);

    this.pendingRequest = sm;
    this.pendingRequestSign = sign;

    if (sm.serializedMessage == null) {
      serializeMessage(sm);
    }

    // Logger.println("Sending message with "+sm.serializedMessage.length+" bytes of
    // content.");

    // produce signature
    if (sign && sm.serializedMessageSignature == null) {
      sm.serializedMessageSignature = signMessage(privKey, sm.serializedMessage);
    }

    int sent = 0;

    for (ReplicaId target : shuffledTargets) {
      // This is done to avoid a race condition with the writeAndFlush method. Since the method
      // is asynchronous, each iteration of this loop could overwrite the destination of the
      // previous one
      sm = sm.clone();

      sm.destination = target.value();

      rl.readLock().lock();
      Channel channel = sessionClientToReplica.get(target.value()).getChannel();
      rl.readLock().unlock();
      if (channel.isActive()) {
        sm.signed = sign;
        ChannelFuture f = channel.writeAndFlush(sm);

        f.addListener(listener);

        sent++;
      } else {
        logger.debug("Channel to {} is not connected", target);
      }
    }

    // FIXME Kai: get F from somewhere else than controller

    //    if (targets.length > controller.getCurrentViewF() && sent < controller.getCurrentViewF() +
    // 1) {
    //      // if less than f+1 servers are connected send an exception to the client
    //      throw new RuntimeException("Impossible to connect to servers!");
    //    }
    if (targets.size() == 1 && sent == 0) throw new RuntimeException("Server not connected");
  }

  /**
   * Serializes the message to the serializedMessage field of the passed ClientMessageWrapper
   * object.
   *
   * @param sm ClientMessageWrapper to be serialized
   */
  private void serializeMessage(ClientMessageWrapper sm) {
    // serialize message
    DataOutputStream dos = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      dos = new DataOutputStream(baos);
      sm.wExternal(dos);
      dos.flush();
      sm.serializedMessage = baos.toByteArray();
    } catch (IOException ex) {
      logger.error("Impossible to serialize message: " + sm);
    }
  }

  /**
   * Serializes the message, signs the serialized message, and then stores the signature in the
   * field serializedMessageSignature.
   *
   * @param sm
   */
  public void sign(ClientMessageWrapper sm) {
    // serialize message
    DataOutputStream dos = null;
    byte[] data = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      dos = new DataOutputStream(baos);
      sm.wExternal(dos);
      dos.flush();
      data = baos.toByteArray();
      sm.serializedMessage = data;
    } catch (IOException ex) {

      logger.error("Failed to sign ClientMessageWrapper", ex);
    }

    // produce signature
    byte[] signature = signMessage(privKey, data);
    sm.serializedMessageSignature = signature;
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
    this.closed = true;
    // Iterator sessions = sessionClientToReplica.values().iterator();
    rl.readLock().lock();
    ArrayList<NettyClientServerSession> sessions = new ArrayList<>(sessionClientToReplica.values());
    rl.readLock().unlock();
    for (NettyClientServerSession ncss : sessions) {
      Channel c = ncss.getChannel();
      closeChannelAndEventLoop(c);
    }
  }

  private ChannelInitializer<SocketChannel> getChannelInitializer()
      throws NoSuchAlgorithmException {

    final NettyClientPipelineFactory nettyClientPipelineFactory =
        new NettyClientPipelineFactory(this, sessionClientToReplica, configManager, rl);

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

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) {
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
  }

  private void scheduleReconnect(final ChannelHandlerContext ctx, int time) {
    if (closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    final EventLoop loop = ctx.channel().eventLoop();
    loop.schedule(
        new Runnable() {
          @Override
          public void run() {
            reconnect(ctx);
          }
        },
        time,
        TimeUnit.SECONDS);
  }

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

      logger.debug(this.remainingFutures + " channel operations remaining to complete");

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
        logger.debug(
            "There are still {} channel operations pending, waiting to complete",
            this.remainingFutures);
        try {
          // timeout if a malicous replica refuses to acknowledge the operation as completed
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
    sessionClientToReplica.put(replicaId, ncss);

    return channelFuture;
  }

  public synchronized void removeClient(int clientId) {
    sessionClientToReplica.remove(clientId);
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

    // if message was not yet serialized, serialize it and cache it in the
    if (sm.serializedMessage == null) {
      serializeMessage(sm);
    }
    if (sign && sm.serializedMessageSignature == null) {
      sm.serializedMessageSignature = signMessage(privKey, sm.serializedMessage);
    }
    sm = sm.clone();
    sm.destination = replicaId;
    rl.readLock().lock();
    Channel channel = sessionClientToReplica.get(replicaId).getChannel();
    rl.readLock().unlock();
    if (channel.isActive()) {
      sm.signed = sign;
      ChannelFuture f = channel.writeAndFlush(sm);
      f.addListener(listener);
    } else {
      logger.info("Channel to {} is not connected", replicaId);
    }
  }
}
