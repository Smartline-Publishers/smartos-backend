package com.smartline.smartos.modules.user.webauthn;

/** Internal signal for malformed or cryptographically invalid WebAuthn data. */
public class WebAuthnException extends RuntimeException {
  public WebAuthnException(String message) {
    super(message);
  }

  public WebAuthnException(String message, Throwable cause) {
    super(message, cause);
  }
}
