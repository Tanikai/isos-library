package isos.consensus.model.viewchange;

import isos.message.replica.fast.DepProposeWithRequest;
import isos.message.replica.fast.DepVerifyMessage;
import isos.message.replica.reconciliation.PrepareMessage;
import isos.utils.ViewNumber;

import java.io.Serializable;
import java.util.List;

public record ReconciliationPathCertificate(
    DepProposeWithRequest originalDepPropose,
    List<DepVerifyMessage> depVerifyMessages,
    List<PrepareMessage> prepareMessages,
    ViewNumber previousViewNumber)
    implements ViewChangeCertificate, Serializable {
  @Override
  public CertificateType certificateType() {
    return CertificateType.RPC;
  }
}
