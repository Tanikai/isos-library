package isos.consensus.model.viewchange;

public record EmptyCertificate() implements  ViewChangeCertificate {
  @Override
  public CertificateType certificate() {
    return CertificateType.NULL;
  }
}
