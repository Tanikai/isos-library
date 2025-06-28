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
import isos.utils.ReplicaId;

import java.util.List;

/**
 * Methods that should be implemented by the client side of the client-server communication system
 *
 * @author Paulo
 */
public interface CommunicationSystemClientSide {
  void send(boolean sign, List<ReplicaId> targets, ClientMessageWrapper sm, int quorumSize);

  void setReplyReceiver(ReplyReceiver trr);

  void sign(ClientMessageWrapper sm);

  void close();

  // ******* EDUARDO BEGIN **************//
  void updateConnections();
  // ******* EDUARDO END **************//
}
