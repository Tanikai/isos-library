package isos.communication.client;

import isos.communication.ClientMessageWrapper;

import java.util.List;

@FunctionalInterface
public interface ReplyExtractor<T> {
  /**
   * Function to extract the final reply of a Multicast. The responses are guaranteed to be the same
   * with the Comparator that was passed to the SingleRequestHandler.
   *
   * @param replies
   * @return
   */
  T extractReply(List<ClientMessageWrapper> replies);
}
