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
package bftsmart.reconfiguration.views;

import java.io.Serializable;
import java.util.Arrays;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * A view is a specific configuration of the system that replicas agree at a given moment. It can
 * for example include: Id Number ("Epoch Number"), Leader (not relevant for ISOS), Replicas
 * participating in the consensus.
 *
 * @author eduardo
 */
public class View implements Serializable {

  private static final long serialVersionUID = 4052550874674512359L;

  private int id;
  private int f;
  private int[] processes;
  private Map<Integer, InetSocketAddress> addresses;

  public View(int id, int[] processes, int f, InetSocketAddress[] addresses) {
    this.id = id;
    this.processes = processes;
    this.addresses = new HashMap<Integer, InetSocketAddress>();

    for (int i = 0; i < this.processes.length; i++) this.addresses.put(processes[i], addresses[i]);
    Arrays.sort(this.processes);
    this.f = f;
  }

  public boolean isMember(int id) {
    for (int i = 0; i < this.processes.length; i++) {
      if (this.processes[i] == id) {
        return true;
      }
    }
    return false;
  }

  public int getPos(int id) {
    for (int i = 0; i < this.processes.length; i++) {
      if (this.processes[i] == id) {
        return i;
      }
    }
    return -1;
  }

  public int getId() {
    return id;
  }

  public int getF() {
    return f;
  }

  public int getN() {
    return this.processes.length;
  }

  public int[] getProcesses() {
    return processes;
  }

  @Override
  public String toString() {
    String ret = "ID:" + id + "; F:" + f + "; Processes:";
    for (int i = 0; i < processes.length; i++) {
      ret = ret + processes[i] + "(" + addresses.get(processes[i]) + "),";
    }

    return ret;
  }

  public InetSocketAddress getAddress(int id) {
    return addresses.get(id);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof View) {
      View v = (View) obj;
      //            return (this.addresses.equals(v.addresses) &&
      return (this.addresses.keySet().equals(v.addresses.keySet())
          && Arrays.equals(this.processes, v.processes)
          && this.id == v.id
          && this.f == v.f);
    }
    return false;
  }

  public int hashCode() {
    int hash = 1;
    hash = hash * 31 + this.id;
    hash = hash * 31 + this.f;
    if (this.processes != null) {
      for (int i = 0; i < this.processes.length; i++) hash = hash * 31 + this.processes[i];
    } else {
      hash = hash * 31 + 0;
    }
    hash = hash * 31 + this.addresses.hashCode();
    return hash;
  }
}
