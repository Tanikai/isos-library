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
import bftsmart.configuration.ConfigurationManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import isos.communication.ClientMessageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Paulo Sousa
 */
public class NettyClientMessageDecoder extends ByteToMessageDecoder {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** number of measures used to calculate statistics */
  // private final int BENCHMARK_PERIOD = 10000;
  private boolean isClient;

  private ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable;
  private ConfigurationManager configManager;
  private boolean firstTime;
  private ReentrantReadWriteLock sessionMapLock;
  private int bytesToSkip;

  public NettyClientMessageDecoder(
      boolean isClient,
      ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable,
      ConfigurationManager configManager,
      ReentrantReadWriteLock sessionMapLock) {
    this.isClient = isClient;
    this.sessionTable = sessionTable;
    this.firstTime = true;
    this.configManager = configManager;
    this.sessionMapLock = sessionMapLock;
    this.bytesToSkip = 0;
    logger.trace(
        "\n\t isClient: {};"
            + "\n\t sessionTable: {};"
            + "\n\t controller: {};"
            + "\n\t firstTime: {};"
            + "\n\t rl: {};"
            + "\n\t signatureSize: {};",
        new Object[] {isClient, sessionTable.toString(), firstTime, sessionMapLock});
  }

  @Override
  protected void decode(ChannelHandlerContext context, ByteBuf buffer, List<Object> list)
      throws Exception {
    // Skip bytes if necessary.
    if (bytesToSkip != 0) {
      int readable = buffer.readableBytes();
      if (readable > bytesToSkip) {
        buffer.skipBytes(bytesToSkip);
        bytesToSkip = 0;
      } else {
        buffer.skipBytes(readable);
        bytesToSkip -= readable;
        return;
      }
    }

    int dataLength = 0;
    do {
      // Wait until the length prefix is available.
      if (buffer.readableBytes() < Integer.BYTES) {
        return;
      }

      dataLength = buffer.getInt(buffer.readerIndex());
      // Logger.println("Receiving message with "+dataLength+" bytes.");

      // Skip the request if it is too large
      if (dataLength > this.configManager.getStaticConf().getMaxRequestSize() && !isClient) {
        logger.warn("Discarding request with " + dataLength + " bytes");
        buffer.skipBytes(Integer.BYTES);
        int readableBytes = buffer.readableBytes();
        if (dataLength >= readableBytes) {
          buffer.skipBytes(readableBytes);
          bytesToSkip = dataLength - readableBytes;
          return;
        } else {
          buffer.skipBytes(dataLength);
          // Now read dataLength again.
        }
      } else {
        break;
      }
    } while (true);

    // Wait until the whole data is available.
    if (buffer.readableBytes() < dataLength + Integer.BYTES) {
      return;
    }

    // Skip the length field because we know it already.
    buffer.skipBytes(Integer.BYTES);

    // SystemMessage
    int size = buffer.readInt();
    byte[] data = new byte[size];
    buffer.readBytes(data);

    // Signature
    byte[] signature = null;
    size = buffer.readInt();
    if (size > 0) {
      signature = new byte[size];
      buffer.readBytes(signature);
    }

    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais); ) {
      SystemMessage sm = (SystemMessage) ois.readObject();

      if (sm instanceof ClientMessageWrapper wrapper) {
        wrapper.serializedMessage = data;
        if (signature != null) {
          wrapper.serializedMessageSignature = signature;
          wrapper.signed = true;
        }

        logger.debug(
            "Decoded reply from "
                + wrapper.getSender()
                + " with sequence number "
                + wrapper.getClientSequence());
      } else if (sm instanceof PingMessage) {
        // do we need to do something special here?
      } else {
        logger.warn("Received unknown class");
      }

      // TODO Kai: should this channel handling really be in the decoder?
      if (!isClient) {
        sessionMapLock.readLock().lock();
        if (!sessionTable.containsKey(sm.getSender())) {
          logger.debug("Add sender {} to sessions", sm.getSender());
          sessionMapLock.readLock().unlock();

          NettyClientServerSession cs =
              new NettyClientServerSession(context.channel(), sm.getSender());

          sessionMapLock.writeLock().lock();
          sessionTable.put(sm.getSender(), cs);
          sessionMapLock.writeLock().unlock();
        } else {
          sessionMapLock.readLock().unlock();
        }
      }

      list.add(sm);
    } catch (Exception ex) {
      logger.error("Failed to decode ClientMessageWrapper", ex);
    }
  }
}
