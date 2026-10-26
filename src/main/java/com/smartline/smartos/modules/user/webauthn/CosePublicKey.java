package com.smartline.smartos.modules.user.webauthn;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

/**
 * A COSE_Key public credential (COSE algorithms ES256, RS256 and EdDSA).
 *
 * <p>Only the public half is ever provided by the authenticator. Signature
 * verification happens here on the server; a client-side "fingerprint check"
 * is explicitly not sufficient.
 */
public final class CosePublicKey {

  private final PublicKey publicKey;
  private final String algorithmName;

  private CosePublicKey(PublicKey publicKey, String algorithmName) {
    this.publicKey = publicKey;
    this.algorithmName = algorithmName;
  }

  public static CosePublicKey parse(byte[] cose) {
    Object decoded = CborDecoder.decode(cose);
    if (!(decoded instanceof Map<?, ?> map)) {
      throw new WebAuthnException("COSE key is not a map");
    }
    // CBOR integers decode as Long, while COSE labels are written as plain
    // ints here. Compare numerically instead of relying on boxed equality.
    int kty = intValue(field(map, 1));
    int alg = intValue(field(map, 3));
    try {
      return switch (kty) {
        case 2 -> parseEc2(map, alg);
        case 3 -> parseRsa(map, alg);
        case 1 -> parseOkp(map, alg);
        default -> throw new WebAuthnException("Unsupported COSE key type: " + kty);
      };
    } catch (WebAuthnException e) {
      throw e;
    } catch (Exception e) {
      throw new WebAuthnException("Invalid COSE public key", e);
    }
  }

  private static CosePublicKey parseEc2(Map<?, ?> map, int alg) throws Exception {
    if (alg != -7) {
      throw new WebAuthnException("Unsupported EC COSE algorithm: " + alg);
    }
    int crv = intValue(field(map, -1));
    if (crv != 1) {
      throw new WebAuthnException("Only P-256 EC keys are supported");
    }
    byte[] x = bytesValue(field(map, -2));
    byte[] y = bytesValue(field(map, -3));
    AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
    params.init(new ECGenParameterSpec("secp256r1"));
    ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
    ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
    PublicKey key = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
    return new CosePublicKey(key, "SHA256withECDSA");
  }

  private static CosePublicKey parseRsa(Map<?, ?> map, int alg) throws Exception {
    if (alg != -257) {
      throw new WebAuthnException("Unsupported RSA COSE algorithm: " + alg);
    }
    BigInteger n = new BigInteger(1, bytesValue(field(map, -1)));
    BigInteger e = new BigInteger(1, bytesValue(field(map, -2)));
    PublicKey key = KeyFactory.getInstance("RSA")
        .generatePublic(new RSAPublicKeySpec(n, e));
    return new CosePublicKey(key, "SHA256withRSA");
  }

  private static CosePublicKey parseOkp(Map<?, ?> map, int alg) throws Exception {
    if (alg != -8) {
      throw new WebAuthnException("Unsupported OKP COSE algorithm: " + alg);
    }
    int crv = intValue(field(map, -1));
    if (crv != 6) {
      throw new WebAuthnException("Only Ed25519 OKP keys are supported");
    }
    byte[] x = bytesValue(field(map, -2));
    if (x.length != 32) {
      throw new WebAuthnException("Invalid Ed25519 public key length");
    }
    // SubjectPublicKeyInfo prefix for Ed25519 (RFC 8410).
    byte[] prefix = hex("302a300506032b6570032100");
    byte[] encoded = new byte[prefix.length + x.length];
    System.arraycopy(prefix, 0, encoded, 0, prefix.length);
    System.arraycopy(x, 0, encoded, prefix.length, x.length);
    PublicKey key = KeyFactory.getInstance("Ed25519")
        .generatePublic(new X509EncodedKeySpec(encoded));
    return new CosePublicKey(key, "Ed25519");
  }

  public boolean verify(byte[] data, byte[] signature) {
    try {
      Signature verifier = Signature.getInstance(algorithmName);
      verifier.initVerify(publicKey);
      verifier.update(data);
      return verifier.verify(signature);
    } catch (Exception e) {
      return false;
    }
  }

  /** Verifies with an arbitrary public key using a COSE alg id (packed x5c). */
  public static boolean verifyWith(PublicKey key, int alg, byte[] data, byte[] signature) {
    try {
      Signature verifier = Signature.getInstance(algorithmName(alg));
      verifier.initVerify(key);
      verifier.update(data);
      return verifier.verify(signature);
    } catch (Exception e) {
      return false;
    }
  }

  public static String algorithmName(int alg) {
    return switch (alg) {
      case -7 -> "SHA256withECDSA";
      case -257 -> "SHA256withRSA";
      case -8 -> "Ed25519";
      default -> throw new WebAuthnException("Unsupported COSE algorithm: " + alg);
    };
  }

  /** Finds a COSE label regardless of the numeric wrapper type used by CBOR. */
  private static Object field(Map<?, ?> map, int label) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() instanceof Number number && number.longValue() == label) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static int intValue(Object value) {
    if (!(value instanceof Number number)) {
      throw new WebAuthnException("COSE key is missing an integer field");
    }
    return number.intValue();
  }

  private static byte[] bytesValue(Object value) {
    if (!(value instanceof byte[] bytes)) {
      throw new WebAuthnException("COSE key is missing a byte-string field");
    }
    return bytes;
  }

  private static byte[] hex(String value) {
    int len = value.length();
    byte[] out = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      out[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
    }
    return out;
  }

  @Override
  public String toString() {
    return "CosePublicKey[" + publicKey.getAlgorithm() + ", " + algorithmName + "]";
  }
}
