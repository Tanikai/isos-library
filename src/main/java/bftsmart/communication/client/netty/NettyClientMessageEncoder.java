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
import bftsmart.communication.server.PingMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import isos.communication.ClientMessageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NettyClientMessageEncoder extends MessageToByteEncoder<SystemMessage> {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private boolean isClient;
  private ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable;
  private ReentrantReadWriteLock rl;

  public NettyClientMessageEncoder(
      boolean isClient,
      ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable,
      ReentrantReadWriteLock rl) {
    this.isClient = isClient;
    this.sessionTable = sessionTable;
    this.rl = rl;
  }

  @Override
  protected void encode(ChannelHandlerContext context, SystemMessage sm, ByteBuf buffer)
      throws Exception {
    byte[] msgData;
    byte[] signatureData = null;

    if (sm instanceof ClientMessageWrapper wrapper) {
      msgData = wrapper.serializedMessage;
      if (wrapper.signed) {
        // signature was already produced before
        signatureData = wrapper.serializedMessageSignature;
      }
    } else if (sm instanceof PingMessage pingMsg) {
      msgData = PingMessage.toByteArray(pingMsg);
      // pingmessage without signature
    } else {
      logger.warn("Cannot encode SystemMessage that is not ClientMessageWrapper / PingMessage");
      return;
    }

    int dataLength =
        Integer.BYTES // integer of message length
            + msgData.length // actual message
            + Integer.BYTES // integer of signature length
            + (signatureData != null ? signatureData.length : 0);

    /* msg size */
    buffer.writeInt(dataLength);

    /* data to be sent */
    buffer.writeInt(msgData.length);
    buffer.writeBytes(msgData);

    /* signature */
    if (signatureData != null) {
      buffer.writeInt(signatureData.length);
      buffer.writeBytes(signatureData);
    } else {
      buffer.writeInt(0);
    }

    context.flush();
  }
}
