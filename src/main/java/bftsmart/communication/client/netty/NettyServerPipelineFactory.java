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

import bftsmart.configuration.ConfigurationManager;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NettyServerPipelineFactory {

  private final ConfigurationManager configManager;
  private final NettyClientServerCommunicationSystemServerSide ncs;
  private final ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable;
  private final ReentrantReadWriteLock rl;

  public NettyServerPipelineFactory(
      NettyClientServerCommunicationSystemServerSide ncs,
      ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable,
      ConfigurationManager configManager,
      ReentrantReadWriteLock rl) {
    this.configManager = configManager;
    this.ncs = ncs;
    this.sessionTable = sessionTable;
    this.rl = rl;
  }

  public ByteToMessageDecoder getDecoder() {
    return new NettyClientMessageDecoder(false, sessionTable, configManager, rl);
  }

  public MessageToByteEncoder getEncoder() {
    return new NettyClientMessageEncoder(false, sessionTable, rl);
  }

  public SimpleChannelInboundHandler getHandler() {
    return ncs;
  }
}
