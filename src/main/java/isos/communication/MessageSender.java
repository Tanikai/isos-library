package isos.communication;

import bftsmart.communication.SystemMessage;
import isos.utils.ReplicaId;

/**
 * Interface for Queue Processors to send messages to other replicas after they've handled a
 * message. The functions have to be thread-safe.
 */
public interface MessageSender {
  void sendToReplicas(ReplicaId[] targets, SystemMessage sm);

  void broadcastToReplicas(boolean includeSelf, SystemMessage sm);
}
