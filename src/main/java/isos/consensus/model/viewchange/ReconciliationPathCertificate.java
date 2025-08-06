package isos.consensus.model.viewchange;

import isos.message.replica.fast.DepProposeMessage;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.utils.ViewNumber;

import java.util.List;

public record ReconciliationPathCertificate(
    DepProposeMessage originalDepPropose,
    List<DepVerifyMessage> depVerifyMessages,
    List<PrepareMessage> prepareMessages,
    ViewNumber currentSlotViewNumber)
    implements ViewChangeCertificate {
  @Override
  public CertificateType certificate() {
    return CertificateType.RECONCILIATION_PATH_CERTIFICATE;
  }
}
