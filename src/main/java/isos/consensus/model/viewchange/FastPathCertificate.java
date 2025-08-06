package isos.consensus.model.viewchange;

import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import java.util.List;

/**
 *
 * @param originalDepPropose DepPropose message from the original coordinator
 * @param depVerifyMessages Set of 2f matching DepVerify messages from different followers
 * @param unknownParameter -1 (?)
 */
public record FastPathCertificate(DepProposeMessage originalDepPropose, List<DepVerifyMessage> depVerifyMessages, int unknownParameter) implements ViewChangeCertificate {
  @Override
  public CertificateType certificate() {
    return CertificateType.FAST_PATH_CERTIFICATE;
  }
}
