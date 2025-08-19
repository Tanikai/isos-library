package isos.consensus.model.viewchange;

import isos.utils.ViewNumber;

import java.io.Serializable;

public record EmptyCertificate() implements ViewChangeCertificate, Serializable {
  @Override
  public CertificateType certificateType() {
    return CertificateType.NULL;
  }

  @Override
  public ViewNumber previousViewNumber() {
    return null;
  }
}
