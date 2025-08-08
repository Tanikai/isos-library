package isos.consensus.model.viewchange;

import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.utils.ViewNumber;

import java.io.Serializable;
import java.util.List;

/**
 * @param originalDepPropose DepPropose message from the original coordinator
 * @param depVerifyMessages Set of 2f matching DepVerify messages from different followers
 * (@param viewNumber Constant -1)
 */
public record FastPathCertificate(
    DepProposeMessage originalDepPropose,
    List<DepVerifyMessage> depVerifyMessages)
    implements ViewChangeCertificate, Serializable {
  @Override
  public CertificateType certificate() {
    return CertificateType.FAST_PATH_CERTIFICATE;
  }

  @Override
  public ViewNumber previousViewNumber() {
    return new ViewNumber(-1);
  }
}
