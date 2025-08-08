package isos.consensus.model.viewchange;

import isos.utils.ViewNumber;

public interface ViewChangeCertificate {
  CertificateType certificate();
  ViewNumber previousViewNumber();
}
