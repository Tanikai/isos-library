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

import isos.message.ClientMessageWrapper;

/** Interface that receives requests/commands from Clients. See {@link ReplyReceiver} for the counterpart. */
public interface RequestReceiver {

  /**
   * This method is invoked by the CommunicationSystemServerSide, to deliver a client request. It
   * assumes that the communication system delivers the message in FIFO order. Code to handle
   * requests should be put here.
   *
   * @param msg The request being received from the client
   * @param fromClient Whether the request was received from a client or was part of a forwarded
   *     message. If it was forwarded, it should not be dropped.
   */
  void requestReceived(ClientMessageWrapper msg, boolean fromClient);
}
