package com.smartline.smartos.modules.user.webauthn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier.AssertionResult;
import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier.RegisteredCredential;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebAuthnVerifierTest {

  private static final String RP_ID = "localhost";
  private static final String ORIGIN = "http://localhost:3000";
  private static final Set<String> ORIGINS = Set.of(ORIGIN);
  private static final byte[] CREDENTIAL_ID = new byte[] {
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88,
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88
  };

  @Test
  void verifiesRegistrationAndExtractsTheCredential() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x45, 0, CREDENTIAL_ID, cose);
    byte[] clientData = clientData("webauthn.create", "challenge-register", ORIGIN, false);

    RegisteredCredential registered = WebAuthnVerifier.verifyRegistration(
        clientData, attestation("none", authData), RP_ID, ORIGINS);

    assertEquals(WebAuthnVerifier.Base64Url.encode(CREDENTIAL_ID), registered.credentialId());
    assertArrayEquals(cose, registered.publicKeyCose());
    assertEquals(0, registered.signCount());
    assertFalse(registered.backupEligible());
    assertEquals(null, registered.aaguid());
  }

  @Test
  void registrationRejectsAnUnexpectedOriginOrRpId() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x45, 0, CREDENTIAL_ID, cose);

    WebAuthnException origin = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(
            clientData("webauthn.create", "c", "http://evil.example", false),
            attestation("none", authData), RP_ID, ORIGINS));
    assertTrue(origin.getMessage().contains("origin"));

    WebAuthnException rp = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(
            clientData("webauthn.create", "c", ORIGIN, false),
            attestation("none", authData), "example.com", ORIGINS));
    assertTrue(rp.getMessage().contains("RP ID"));
  }

  @Test
  void registrationRejectsMissingUserPresenceAndCredentialData() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] clientData = clientData("webauthn.create", "c", ORIGIN, false);

    WebAuthnException noPresence = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(clientData,
            attestation("none", authData(0x44, 0, CREDENTIAL_ID, cose)), RP_ID, ORIGINS));
    assertTrue(noPresence.getMessage().contains("presence"));

    WebAuthnException noCredential = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(clientData,
            attestation("none", authData(0x41, 0, null, null)), RP_ID, ORIGINS));
    assertTrue(noCredential.getMessage().contains("credential data"));
  }

  @Test
  void registrationRejectsWrongCeremonyCrossOriginAndUnsupportedAttestation() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x45, 0, CREDENTIAL_ID, cose);

    assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(
            clientData("webauthn.get", "c", ORIGIN, false),
            attestation("none", authData), RP_ID, ORIGINS));

    assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(
            clientData("webauthn.create", "c", ORIGIN, true),
            attestation("none", authData), RP_ID, ORIGINS));

    assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyRegistration(
            clientData("webauthn.create", "c", ORIGIN, false),
            attestation("tpm", authData), RP_ID, ORIGINS));
  }

  @Test
  void verifiesAnAssertionSignature() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x05, 7, null, null);
    byte[] clientData = clientData("webauthn.get", "challenge-login", ORIGIN, false);
    byte[] signature = sign(keyPair, concat(authData, sha256(clientData)));

    AssertionResult result = WebAuthnVerifier.verifyAssertion(clientData, authData, signature,
        CosePublicKey.parse(cose), 3, RP_ID, ORIGINS, true);

    assertEquals(7, result.signCount());
    assertTrue(result.userVerified());
    assertFalse(result.backupState());
  }

  @Test
  void assertionRejectsBadSignaturesAndReplayedCounters() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x05, 7, null, null);
    byte[] clientData = clientData("webauthn.get", "challenge-login", ORIGIN, false);
    byte[] signedData = concat(authData, sha256(clientData));

    byte[] badSignature = sign(ecKeyPair(), signedData);
    assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyAssertion(clientData, authData, badSignature,
            CosePublicKey.parse(cose), 3, RP_ID, ORIGINS, true));

    byte[] validSignature = sign(keyPair, signedData);
    WebAuthnException counter = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyAssertion(clientData, authData, validSignature,
            CosePublicKey.parse(cose), 7, RP_ID, ORIGINS, true));
    assertTrue(counter.getMessage().contains("counter"));
  }

  @Test
  void assertionEnforcesUserVerificationAndOrigin() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x01, 2, null, null); // UP only, no UV
    byte[] clientData = clientData("webauthn.get", "challenge", ORIGIN, false);
    byte[] signature = sign(keyPair, concat(authData, sha256(clientData)));

    assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyAssertion(clientData, authData, signature,
            CosePublicKey.parse(cose), 1, RP_ID, ORIGINS, true));

    // A trailing slash on the configured/observed origin is tolerated.
    WebAuthnVerifier.verifyAssertion(
        clientData("webauthn.get", "challenge", ORIGIN + "/", false),
        authData, sign(keyPair, concat(authData, sha256(
            clientData("webauthn.get", "challenge", ORIGIN + "/", false)))),
        CosePublicKey.parse(cose), 1, RP_ID, ORIGINS, false);
  }

  @Test
  void rejectsClientDataWithoutAChallenge() throws Exception {
    KeyPair keyPair = ecKeyPair();
    byte[] cose = coseEc2((ECPublicKey) keyPair.getPublic());
    byte[] authData = authData(0x05, 3, null, null);
    byte[] clientData = ("{\"type\":\"webauthn.get\",\"origin\":\"" + ORIGIN + "\"}")
        .getBytes(StandardCharsets.UTF_8);

    WebAuthnException error = assertThrows(WebAuthnException.class,
        () -> WebAuthnVerifier.verifyAssertion(clientData, authData, new byte[] {1, 2, 3},
            CosePublicKey.parse(cose), 0, RP_ID, ORIGINS, true));
    assertTrue(error.getMessage().contains("challenge"));
  }

  // ---- helpers ----

  private static KeyPair ecKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static byte[] coseEc2(ECPublicKey key) {
    byte[] x = unsigned(key.getW().getAffineX());
    byte[] y = unsigned(key.getW().getAffineY());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeTypeAndLength(out, 5, 5);
    writeInt(out, 1);
    writeInt(out, 2);  // kty: EC2
    writeInt(out, 3);
    writeInt(out, -7); // alg: ES256
    writeInt(out, -1);
    writeInt(out, 1);  // crv: P-256
    writeInt(out, -2);
    writeBytes(out, x);
    writeInt(out, -3);
    writeBytes(out, y);
    return out.toByteArray();
  }

  private static byte[] unsigned(BigInteger value) {
    byte[] raw = value.toByteArray();
    byte[] out = new byte[32];
    int copy = Math.min(raw.length, 32);
    System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
    return out;
  }

  private static byte[] authData(int flags, long signCount, byte[] credentialId, byte[] cose) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(sha256(RP_ID.getBytes(StandardCharsets.UTF_8)));
    out.write(flags);
    out.write((int) (signCount >>> 24) & 0xff);
    out.write((int) (signCount >>> 16) & 0xff);
    out.write((int) (signCount >>> 8) & 0xff);
    out.write((int) signCount & 0xff);
    if (credentialId != null) {
      out.writeBytes(new byte[16]); // AAGUID
      out.write((credentialId.length >>> 8) & 0xff);
      out.write(credentialId.length & 0xff);
      out.writeBytes(credentialId);
      out.writeBytes(cose);
    }
    return out.toByteArray();
  }

  private static byte[] attestation(String format, byte[] authData) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeTypeAndLength(out, 5, 3);
    writeText(out, "fmt");
    writeText(out, format);
    writeText(out, "attStmt");
    writeTypeAndLength(out, 5, 0);
    writeText(out, "authData");
    writeBytes(out, authData);
    return out.toByteArray();
  }

  private static byte[] clientData(String type, String challenge, String origin,
      boolean crossOrigin) {
    return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge + "\",\"origin\":\""
        + origin + "\"" + (crossOrigin ? ",\"crossOrigin\":true" : "") + "}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] sign(KeyPair keyPair, byte[] data) throws Exception {
    Signature signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(data);
    return signer.sign();
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void writeText(ByteArrayOutputStream out, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    writeTypeAndLength(out, 3, bytes.length);
    out.writeBytes(bytes);
  }

  private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
    writeTypeAndLength(out, 2, bytes.length);
    out.writeBytes(bytes);
  }

  private static void writeInt(ByteArrayOutputStream out, long value) {
    if (value >= 0) {
      writeTypeAndLength(out, 0, value);
    } else {
      writeTypeAndLength(out, 1, -1 - value);
    }
  }

  private static void writeTypeAndLength(ByteArrayOutputStream out, int major, long length) {
    int type = major << 5;
    if (length < 24) {
      out.write(type | (int) length);
    } else if (length <= 0xff) {
      out.write(type | 24);
      out.write((int) length);
    } else if (length <= 0xffff) {
      out.write(type | 25);
      out.write((int) (length >>> 8) & 0xff);
      out.write((int) length & 0xff);
    } else {
      out.write(type | 26);
      out.write((int) (length >>> 24) & 0xff);
      out.write((int) (length >>> 16) & 0xff);
      out.write((int) (length >>> 8) & 0xff);
      out.write((int) length & 0xff);
    }
  }
}
