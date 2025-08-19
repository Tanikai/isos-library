package isos.consensus;

import isos.utils.ViewNumber;

public class ViewNumberNotLargerException extends RuntimeException {
  public ViewNumberNotLargerException(ViewNumber currentViewNumber, ViewNumber newViewNumber) {
    super(String.format("New view number %s must be larger than current view number %s!", newViewNumber, currentViewNumber));
  }
}
