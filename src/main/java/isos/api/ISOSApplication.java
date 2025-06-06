package isos.api;

import isos.message.ClientRequest;

/**
 *
 * TODO Kai: Maybe interface instead of class?
 */
public class ISOSApplication {


  /**
   * Has to be overwritten. Requires application-specific information whether two requests conflict
   * with each other or not (e.g., writes to the same key in a KV-store)
   * @param a
   * @param b
   * @return
   */
  public boolean conflict(ClientRequest a, ClientRequest b) {
    return true;
  }
}
