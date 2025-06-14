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
package bftsmart.tom.util;

import bftsmart.reconfiguration.util.TOMConfiguration;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import org.apache.commons.codec.binary.Base64;

/**
 * Utility class used to generate a key pair for some process id on config/keys/publickey<id> and
 * config/keys/privatekey<id>
 */
public class ECDSAKeyPairGenerator {

  /** Creates a new instance of KeyPairGenerator */
  public ECDSAKeyPairGenerator() {}

  /**
   * Generate the key pair for the process with id = <id> and put it on the files
   * config/keys/publickey<id> and config/keys/privatekey<id>
   *
   * @param id the id of the process to generate key
   * @throws Exception something goes wrong when writing the files
   */
  public void run(int id, String domainParam, String provider) throws Exception {

    ECGenParameterSpec specs = new ECGenParameterSpec(domainParam);
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", provider);
    keyGen.initialize(specs);

    KeyPair kp = keyGen.generateKeyPair();
    PublicKey puk = kp.getPublic();
    PrivateKey prk = kp.getPrivate();
    saveToFile(id, puk, prk);
  }

  private void saveToFile(int id, PublicKey puk, PrivateKey prk) throws Exception {
    String path =
        "config"
            + System.getProperty("file.separator")
            + "keysECDSA"
            + System.getProperty("file.separator");

    BufferedWriter w = new BufferedWriter(new FileWriter(path + "publickey" + id, false));
    w.write(getKeyAsString(puk));
    w.flush();
    w.close();

    w = new BufferedWriter(new FileWriter(path + "privatekey" + id, false));
    w.write(getKeyAsString(prk));
    w.flush();
    w.close();
  }

  private String getKeyAsString(Key key) {
    byte[] keyBytes = key.getEncoded();

    return Base64.encodeBase64String(keyBytes);
  }

  public static void main(String[] args) throws Exception {

    if (args.length < 2)
      System.err.println("Use: ECDSAKeyPairGenerator <id> <domain parameter> [config dir]");
    String confHome = "";
    if (args.length > 2) confHome = args[2];

    TOMConfiguration conf = new TOMConfiguration(Integer.parseInt(args[0]), confHome, null);
    String provider = conf.getSignatureAlgorithmProvider();

    new ECDSAKeyPairGenerator().run(Integer.parseInt(args[0]), args[1], provider);
  }
}
