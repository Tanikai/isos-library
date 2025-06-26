package isos.api;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.configuration.ConfigurationManager;
import bftsmart.tom.util.KeyLoader;
import isos.message.ClientMessageWrapper;
import isos.message.ClientRequest;
import isos.message.OrderedClientRequest;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the ISOS equivalent of the {@link bftsmart.tom.core.TOMSender}.
 */
public class ISOSClient implements ReplyReceiver, Closeable, AutoCloseable {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final CommunicationSystemClientSide ccs;
  private final ConfigurationManager configManager;
  private final int ownClientId;
  private final boolean useSignatures; // Should we sign our requests or not?
  private final int session; // session id
  
  public ISOSClient(int processId, String configHome, KeyLoader loader) {
    if (configHome == null) {
      this.configManager = new ConfigurationManager(processId, loader);
    } else {
      this.configManager = new ConfigurationManager(processId, configHome, loader);
    }
    this.ownClientId = this.configManager.getStaticConf().getProcessId();
    this.ccs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(
                    processId, this.configManager);
    this.ccs.setReplyReceiver(this); // This object itself shall be a reply receiver
    this.useSignatures = this.configManager.getStaticConf().getUseSignatures() == 1;
    this.session = new Random().nextInt();
  }

  public void close() {
    this.ccs.close();
  }

  public void sendRequest(ClientRequest r) {

  }

  @Override
  public void replyReceived(ClientMessageWrapper reply) {
    var payload = reply.getPayload();

    try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
         ObjectInputStream ois = new ObjectInputStream(bis)) {
      
      OrderedClientRequest r = (OrderedClientRequest) ois.readObject();

    } catch (IOException e) {
      // FIXME
      logger.warn("");
    } catch (ClassNotFoundException e) {
      // FIXME
      logger.warn("");
    }
  }
}
