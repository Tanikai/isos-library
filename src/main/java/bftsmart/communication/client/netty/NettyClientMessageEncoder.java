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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import isos.communication.ClientMessageWrapper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyClientMessageEncoder extends MessageToByteEncoder<ClientMessageWrapper> {
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
  protected void encode(ChannelHandlerContext context, ClientMessageWrapper sm, ByteBuf buffer)
      throws Exception {
    byte[] msgData;
    byte[] signatureData = null;

    msgData = sm.serializedMessage;
    if (sm.signed) {
      // signature was already produced before
      signatureData = sm.serializedMessageSignature;
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
