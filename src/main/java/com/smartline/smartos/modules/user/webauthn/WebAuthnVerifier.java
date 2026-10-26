package com.smartline.smartos.modules.user.webauthn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side WebAuthn/FIDO2 verification.
 *
 * <p>Validates client-data type/origin, RP ID hash, user-presence (and
 * optionally user-verification) flags, attestation signatures, assertion
 * signatures and the signature counter. The challenge itself is validated by
 * the caller against the single-use challenge store.
 */
public final class WebAuthnVerifier {

  private static final ObjectMapper JSON = new ObjectMapper();

  public record RegisteredCredential(
      String credentialId,
      byte[] publicKeyCose,
      long signCount,
      String aaguid,
      boolean backupEligible,
      boolean backupState) {
  }

  public record AssertionResult(
      long signCount,
      boolean userVerified,
      boolean backupState) {
  }

  private WebAuthnVerifier() {
  }

  public static RegisteredCredential verifyRegistration(byte[] clientDataJson,
      byte[] attestationObject, String rpId, Set<String> allowedOrigins) {
    JsonNode client = parseClientData(clientDataJson, "webauthn.create", allowedOrigins);
    requireChallenge(client);

    Map<?, ?> attestation = asMap(CborDecoder.decode(attestationObject), "attestation object");
    String format = asString(attestation.get("fmt"), "attestation format");
    byte[] authData = asBytes(attestation.get("authData"), "authenticator data");
    Map<?, ?> attStmt = attestation.get("attStmt") == null
        ? Map.of()
        : asMap(attestation.get("attStmt"), "attestation statement");

    AuthenticatorData parsed = parseAuthenticatorData(authData, rpId);
    if (!parsed.userPresent) {
      throw new WebAuthnException("User presence was not asserted");
    }
    if (!parsed.attestedCredentialData) {
      throw new WebAuthnException("Authenticator did not return credential data");
    }

    int offset = 37;
    if (authData.length < offset + 18) {
      throw new WebAuthnException("Truncated attested credential data");
    }
    byte[] aaguid = Arrays.copyOfRange(authData, offset, offset + 16);
    offset += 16;
    int credentialIdLength = ((authData[offset] & 0xff) << 8) | (authData[offset + 1] & 0xff);
    offset += 2;
    if (credentialIdLength <= 0 || credentialIdLength > 1023
        || authData.length < offset + credentialIdLength + 1) {
      throw new WebAuthnException("Invalid credential id length");
    }
    byte[] credentialId = Arrays.copyOfRange(authData, offset, offset + credentialIdLength);
    offset += credentialIdLength;

    CborDecoder.Decoded cose = CborDecoder.decodeAt(authData, offset);
    byte[] coseBytes = Arrays.copyOfRange(authData, offset, cose.nextOffset());
    CosePublicKey publicKey = CosePublicKey.parse(coseBytes);

    byte[] clientDataHash = sha256(clientDataJson);
    verifyAttestation(format, attStmt, authData, clientDataHash, publicKey);

    return new RegisteredCredential(
        Base64Url.encode(credentialId),
        coseBytes,
        parsed.signCount,
        isZero(aaguid) ? null : hex(aaguid),
        parsed.backupEligible,
        parsed.backupState);
  }

  public static AssertionResult verifyAssertion(byte[] clientDataJson, byte[] authData,
      byte[] signature, CosePublicKey publicKey, long storedSignCount, String rpId,
      Set<String> allowedOrigins, boolean requireUserVerification) {
    JsonNode client = parseClientData(clientDataJson, "webauthn.get", allowedOrigins);
    requireChallenge(client);
    AuthenticatorData parsed = parseAuthenticatorData(authData, rpId);

    if (!parsed.userPresent) {
      throw new WebAuthnException("User presence was not asserted");
    }
    if (requireUserVerification && !parsed.userVerified) {
      throw new WebAuthnException("User verification is required");
    }

    byte[] signedData = concat(authData, sha256(clientDataJson));
    if (!publicKey.verify(signedData, signature)) {
      throw new WebAuthnException("Invalid assertion signature");
    }

    // Spec: a non-zero counter must increase strictly. A cloned authenticator
    // replays an old counter, which is rejected here.
    if ((storedSignCount != 0 || parsed.signCount != 0) && parsed.signCount <= storedSignCount) {
      throw new WebAuthnException("Authenticator signature counter did not increase");
    }

    return new AssertionResult(parsed.signCount, parsed.userVerified, parsed.backupState);
  }

  private static JsonNode parseClientData(byte[] clientDataJson, String expectedType,
      Set<String> allowedOrigins) {
    JsonNode client;
    try {
      client = JSON.readTree(clientDataJson);
    } catch (Exception e) {
      throw new WebAuthnException("Invalid client data JSON");
    }
    if (!expectedType.equals(text(client, "type"))) {
      throw new WebAuthnException("Unexpected WebAuthn ceremony type");
    }
    String origin = text(client, "origin");
    if (origin == null || !isAllowedOrigin(origin, allowedOrigins)) {
      throw new WebAuthnException("WebAuthn origin is not allowed");
    }
    JsonNode crossOrigin = client.get("crossOrigin");
    if (crossOrigin != null && crossOrigin.asBoolean(false)) {
      throw new WebAuthnException("Cross-origin WebAuthn is not allowed");
    }
    return client;
  }

  private static void requireChallenge(JsonNode client) {
    String challenge = text(client, "challenge");
    if (challenge == null || challenge.isBlank()) {
      throw new WebAuthnException("Missing WebAuthn challenge");
    }
  }

  private static boolean isAllowedOrigin(String origin, Set<String> allowedOrigins) {
    String normalized = stripTrailingSlash(origin);
    Set<String> normalizedAllowed = new LinkedHashSet<>();
    for (String allowed : allowedOrigins) {
      if (allowed != null && !allowed.isBlank()) {
        normalizedAllowed.add(stripTrailingSlash(allowed.trim()));
      }
    }
    return normalizedAllowed.contains(normalized);
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static AuthenticatorData parseAuthenticatorData(byte[] authData, String rpId) {
    if (authData.length < 37) {
      throw new WebAuthnException("Truncated authenticator data");
    }
    byte[] expectedRpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
    byte[] actualRpIdHash = Arrays.copyOfRange(authData, 0, 32);
    if (!MessageDigest.isEqual(expectedRpIdHash, actualRpIdHash)) {
      throw new WebAuthnException("RP ID hash mismatch");
    }
    int flags = authData[32] & 0xff;
    long signCount = ((long) (authData[33] & 0xff) << 24)
        | ((long) (authData[34] & 0xff) << 16)
        | ((long) (authData[35] & 0xff) << 8)
        | (authData[36] & 0xff);
    return new AuthenticatorData(
        (flags & 0x01) != 0,
        (flags & 0x04) != 0,
        (flags & 0x40) != 0,
        (flags & 0x08) != 0,
        (flags & 0x10) != 0,
        signCount);
  }

  private static void verifyAttestation(String format, Map<?, ?> attStmt, byte[] authData,
      byte[] clientDataHash, CosePublicKey credentialKey) {
    switch (format) {
      case "none" -> {
        // No attestation statement to verify; the credential key is still
        // bound to the registration by the signed client data challenge.
      }
      case "packed" -> verifyPacked(attStmt, authData, clientDataHash, credentialKey);
      default -> throw new WebAuthnException("Unsupported attestation format: " + format);
    }
  }

  private static void verifyPacked(Map<?, ?> attStmt, byte[] authData, byte[] clientDataHash,
      CosePublicKey credentialKey) {
    int alg = asInt(attStmt.get("alg"), "packed alg");
    byte[] signature = asBytes(attStmt.get("sig"), "packed signature");
    byte[] signedData = concat(authData, clientDataHash);
    Object x5cValue = attStmt.get("x5c");
    if (x5cValue instanceof List<?> x5c && !x5c.isEmpty()) {
      if (!(x5c.get(0) instanceof byte[] leafBytes)) {
        throw new WebAuthnException("Invalid packed attestation certificate");
      }
      try {
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(leafBytes));
        certificate.checkValidity();
        if (!CosePublicKey.verifyWith(certificate.getPublicKey(), alg, signedData, signature)) {
          throw new WebAuthnException("Invalid packed attestation signature");
        }
      } catch (WebAuthnException e) {
        throw e;
      } catch (Exception e) {
        throw new WebAuthnException("Could not verify packed attestation", e);
      }
      return;
    }
    // Self-attestation: the credential key signs its own authData.
    CosePublicKey parser = credentialKey;
    if (parser == null || !parser.verify(signedData, signature)) {
      throw new WebAuthnException("Invalid self attestation signature");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static Map<?, ?> asMap(Object value, String name) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new WebAuthnException("Invalid " + name);
    }
    return map;
  }

  private static byte[] asBytes(Object value, String name) {
    if (!(value instanceof byte[] bytes)) {
      throw new WebAuthnException("Invalid " + name);
    }
    return bytes;
  }

  private static String asString(Object value, String name) {
    if (!(value instanceof String string)) {
      throw new WebAuthnException("Invalid " + name);
    }
    return string;
  }

  private static int asInt(Object value, String name) {
    if (!(value instanceof Number number)) {
      throw new WebAuthnException("Invalid " + name);
    }
    return number.intValue();
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

  private static boolean isZero(byte[] bytes) {
    for (byte value : bytes) {
      if (value != 0) {
        return false;
      }
    }
    return true;
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      sb.append(Character.forDigit((value >> 4) & 0xf, 16));
      sb.append(Character.forDigit(value & 0xf, 16));
    }
    return sb.toString();
  }

  private record AuthenticatorData(boolean userPresent, boolean userVerified,
      boolean attestedCredentialData, boolean backupEligible, boolean backupState,
      long signCount) {
  }

  /** Base64url helpers (WebAuthn JSON encoding). */
  public static final class Base64Url {
    private Base64Url() {
    }

    public static String encode(byte[] bytes) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] decode(String value) {
      if (value == null) {
        throw new WebAuthnException("Missing base64url value");
      }
      String normalized = value.trim().replace('-', '+').replace('_', '/');
      int padding = (4 - normalized.length() % 4) % 4;
      normalized = normalized + "=".repeat(padding);
      try {
        return Base64.getDecoder().decode(normalized);
      } catch (IllegalArgumentException e) {
        throw new WebAuthnException("Invalid base64url value");
      }
    }
  }

  // Exposed for tests that need to decode a stored credential id.
  public static byte[] decodeCredentialId(String credentialId) {
    return Base64Url.decode(credentialId);
  }
}
