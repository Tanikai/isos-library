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
package bftsmart.communication.client;

import isos.communication.ClientMessageWrapper;

/**
 * Interface for clients to receive messages/replies from replicas. See {@link RequestReceiver} for
 * the counterpart.
 */
public interface ReplyReceiver {

  /**
   * This is the method invoked by the client side communication system, and where the code to
   * handle the reply is to be written
   *
   * @param reply The reply delivered by the client side communication system
   */
  public void replyReceived(ClientMessageWrapper reply);
}
