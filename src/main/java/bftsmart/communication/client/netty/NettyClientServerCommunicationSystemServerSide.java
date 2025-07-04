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

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.TOMUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import isos.communication.ClientMessageWrapper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.security.PrivateKey;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemServerSide
    extends SimpleChannelInboundHandler<ClientMessageWrapper> implements CommunicationSystemServerSide {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** RequestReceiver is the class that receives messages from clients */
  private RequestReceiver requestReceiver;

  private ConcurrentHashMap<Integer, NettyClientServerSession> sessionReplicaToClient;
  private ReentrantReadWriteLock rl;
  private ConfigurationManager configManager;
  private boolean closed = false;
  private Channel mainChannel;

  private NettyServerPipelineFactory serverPipelineFactory;

  /* Tulio Ribeiro */
  private static final int tcpSendBufferSize = 8 * 1024 * 1024;
  private static final int bossThreads =
      8; /* listens and accepts on server socket; workers handle r/w I/O */
  private static final int connectionBacklog =
      1024; /* pending connections boss thread will queue to accept */
  private static final int connectionTimeoutMsec = 40000; /* (40 seconds) */
  private PrivateKey privKey;

  /* Tulio Ribeiro */

  public NettyClientServerCommunicationSystemServerSide(ConfigurationManager configManager) {
    try {
      this.configManager = configManager;
      /* Tulio Ribeiro */
      privKey = configManager.getStaticConf().getPrivateKey();

      sessionReplicaToClient = new ConcurrentHashMap<>();
      rl = new ReentrantReadWriteLock();

      // Configure the server.
      serverPipelineFactory =
          new NettyServerPipelineFactory(this, sessionReplicaToClient, this.configManager, rl);

      EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreads);
      EventLoopGroup workerGroup =
          new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .option(ChannelOption.SO_REUSEADDR, true)
          .option(ChannelOption.SO_KEEPALIVE, true)
          .option(ChannelOption.TCP_NODELAY, true)
          .option(ChannelOption.SO_SNDBUF, tcpSendBufferSize)
          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMsec)
          .option(ChannelOption.SO_BACKLOG, connectionBacklog)
          .childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                  ch.pipeline().addLast(serverPipelineFactory.getDecoder());
                  ch.pipeline().addLast(serverPipelineFactory.getEncoder());
                  ch.pipeline().addLast(serverPipelineFactory.getHandler());
                }
              })
          .childOption(ChannelOption.SO_KEEPALIVE, true)
          .childOption(ChannelOption.TCP_NODELAY, true);
      String myAddress;
      String confAddress =
          configManager
              .getStaticConf()
              .getRemoteAddress(configManager.getStaticConf().getProcessId())
              .getAddress()
              .getHostAddress();

      if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {
        myAddress = InetAddress.getLoopbackAddress().getHostAddress();

      } else if (configManager.getStaticConf().getBindAddress().isEmpty()) {
        myAddress = InetAddress.getLocalHost().getHostAddress();

        // If Netty binds to the loopback address, clients will not be able to connect to replicas.
        // To solve that issue, we bind to the address supplied in config/hosts.config instead.
        if (InetAddress.getByName(myAddress).isLoopbackAddress()
            && !myAddress.equals(confAddress)) {
          myAddress = confAddress;
        }
      } else {
        myAddress = configManager.getStaticConf().getBindAddress();
      }

      int myPort =
          configManager.getStaticConf().getPort(configManager.getStaticConf().getProcessId());

      ChannelFuture f = b.bind(new InetSocketAddress(myAddress, myPort)).sync();

      logger.info("ID = {}", configManager.getStaticConf().getProcessId());
      // FIXME Kai: getCurrentView quorum sizes have to be determined somewhere else

      //      logger.info("N = " + controller.getCurrentViewN());
      //      logger.info("F = " + controller.getCurrentViewF());
      logger.info(
          "Port (client <-> server) = {}",
          configManager.getStaticConf().getPort(configManager.getStaticConf().getProcessId()));
      logger.info(
          "Port (server <-> server) = {}",
          configManager
              .getStaticConf()
              .getServerToServerPort(configManager.getStaticConf().getProcessId()));
      logger.info("requestTimeout = {}", configManager.getStaticConf().getRequestTimeout());
      logger.info("maxBatch = {}", configManager.getStaticConf().getMaxBatchSize());
      if (configManager.getStaticConf().getUseSignatures() == 1) logger.info("Using Signatures");
      else if (configManager.getStaticConf().getUseSignatures() == 2)
        logger.info("Using benchmark signature verification");
      logger.info("Bound replica to IP address {}", myAddress);
      logger.info(
          "Optimizations:  Read-only Requests: {}",
          configManager.getStaticConf().useReadOnlyRequests() ? "enabled" : "disabled");
      // ******* EDUARDO END **************//

      /* Tulio Ribeiro */
      // SSL/TLS
      logger.info(
          "SSL/TLS enabled, protocol version: {}",
          configManager.getStaticConf().getSSLTLSProtocolVersion());

      /* Tulio Ribeiro END */

      mainChannel = f.channel();

    } catch (InterruptedException | UnknownHostException ex) {
      logger.error("Failed to create Netty communication system", ex);
    }
  }

  private void closeChannelAndEventLoop(Channel c) {
    c.flush();
    c.deregister();
    c.close();
    c.eventLoop().shutdownGracefully();
  }

  @Override
  public void shutdown() {

    logger.info("Shutting down Netty system");

    this.closed = true;

    closeChannelAndEventLoop(mainChannel);

    rl.readLock().lock();
    ArrayList<NettyClientServerSession> sessions = new ArrayList<>(sessionReplicaToClient.values());
    rl.readLock().unlock();
    for (NettyClientServerSession ncss : sessions) {

      closeChannelAndEventLoop(ncss.getChannel());
    }

    logger.info("NettyClientServerCommunicationSystemServerSide is halting.");
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

    if (this.closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    if (cause instanceof ClosedChannelException) logger.info("Client connection closed.");
    else if (cause instanceof IOException) {
      logger.error("Impossible to connect to client. (Connection reset by peer)");
    } else {
      logger.error("Connection problem.", cause);
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ClientMessageWrapper sm) throws Exception {
    if (this.closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    // delivers message to RequestReceiver
    if (requestReceiver == null) logger.warn("Request receiver is still null!");
    else requestReceiver.requestReceived(sm, true);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {

    if (this.closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }
    logger.info("Session Created, active clients={}", sessionReplicaToClient.size());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    logger.debug("Channel Inactive");
    if (this.closed) {
      closeChannelAndEventLoop(ctx.channel());
      return;
    }

    // debugSessions();

    rl.writeLock().lock();
    Set<Entry<Integer, NettyClientServerSession>> s = sessionReplicaToClient.entrySet();
    for (Entry<Integer, NettyClientServerSession> m : s) {
      NettyClientServerSession value = m.getValue();
      if (ctx.channel().equals(value.getChannel())) {
        int key = m.getKey();
        toRemove(key);
        break;
      }
    }
    rl.writeLock().unlock();

    logger.debug("Session Closed, active clients=" + sessionReplicaToClient.size());
  }

  public synchronized void toRemove(Integer key) {

    for (Integer cli : sessionReplicaToClient.keySet()) {
      logger.debug(
          "SessionReplicaToClient: Key:{}, Value:{}", cli, sessionReplicaToClient.get(cli));
    }

    logger.debug("Removing client channel with ID = " + key);
    sessionReplicaToClient.remove(key);
  }

  @Override
  public void setRequestReceiver(RequestReceiver tl) {
    this.requestReceiver = tl;
  }

  private void retrySend(int[] targets, ClientMessageWrapper sm, boolean serializeClassHeaders) {
    send(targets, sm, serializeClassHeaders);
  }

  @Override
  public void send(int[] targets, ClientMessageWrapper sm, boolean serializeClassHeaders) {

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
      logger.error("Failed to serialize message.", ex);
    }

    // replies are not signed in the current JBP version
    sm.signed = false;
    // produce signature if necessary (never in the current version)
    if (sm.signed) {
      sm.serializedMessageSignature = TOMUtil.signMessage(privKey, data);
    }

    for (int target : targets) {
      sm = sm.clone();

      rl.readLock().lock();
      if (sessionReplicaToClient.containsKey(target)) {
        sm.destination = target;
        sessionReplicaToClient.get(target).getChannel().writeAndFlush(sm);
      } else {
        logger.debug(
            "Client not into sessionReplicaToClient({}):{}, waiting and retrying.",
            target,
            sessionReplicaToClient.containsKey(target));
        /*
         * ClientSession clientSession = new ClientSession(target, sm); new
         * Thread(clientSession).start();
         */
        // should I wait for the client?
        // cb: the below code fixes an issue that occurs if a replica tries to send a reply back to
        // some client *before* the connection to that client is successfully established. The
        // client may then fail to gather enough responses and run in a timeout. In this fix we
        // periodically retry to send that response

        if (sm.retry > 0) {
          int retryAfterMillis =
              (int)
                  (1000
                      * // Double retry-timeout every time while approaching client's invokeOrdered
                      // timeout
                      ((double) configManager.getStaticConf().getClientInvokeOrderedTimeout()
                          * Math.pow(2, -1 * sm.retry)));
          sm.retry = sm.retry - 1;
          ClientMessageWrapper finalSm = sm;
          TimerTask timertask =
              new TimerTask() {
                @Override
                public void run() {
                  retrySend(targets, finalSm, serializeClassHeaders);
                }
              };
          Timer timer = new Timer("retry");
          timer.schedule(timertask, retryAfterMillis);
        }
      }
      rl.readLock().unlock();
    }
  }

  @Override
  public int[] getClients() {

    rl.readLock().lock();
    Set<Integer> s = sessionReplicaToClient.keySet();
    int[] clients = new int[s.size()];
    Iterator<Integer> it = s.iterator();
    int i = 0;
    while (it.hasNext()) {
      clients[i] = it.next();
      i++;
    }

    rl.readLock().unlock();

    return clients;
  }
}
