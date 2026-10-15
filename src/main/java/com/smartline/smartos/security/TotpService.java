package com.smartline.smartos.security;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP (SHA-1, 30s step, 6 digits) implemented on JDK crypto only,
 * so two-factor authentication needs no new dependencies. Secrets are
 * Base32-encoded (RFC 4648, no padding) for authenticator-app compatibility.
 */
@Service
public class TotpService {

  private static final int SECRET_BYTES = 20;
  private static final int STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int WINDOW_STEPS = 1;
  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private final SecureRandom random = new SecureRandom();

  public String generateSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    random.nextBytes(bytes);
    return base32Encode(bytes);
  }

  public String otpauthUrl(String issuer, String accountName, String secret) {
    String label = urlEncode(issuer) + ":" + urlEncode(accountName);
    return "otpauth://totp/" + label
        + "?secret=" + secret
        + "&issuer=" + urlEncode(issuer)
        + "&digits=" + DIGITS
        + "&period=" + STEP_SECONDS;
  }

  public boolean verify(String base32Secret, String code) {
    return verify(base32Secret, code, Instant.now());
  }

  boolean verify(String base32Secret, String code, Instant now) {
    if (base32Secret == null || code == null) {
      return false;
    }
    String normalized = code.trim();
    if (!normalized.matches("\\d{6}")) {
      return false;
    }
    byte[] key;
    try {
      key = base32Decode(base32Secret.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return false;
    }
    long step = now.getEpochSecond() / STEP_SECONDS;
    for (long s = step - WINDOW_STEPS; s <= step + WINDOW_STEPS; s++) {
      if (codeFor(key, s).equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  /** Current code for a secret (public for tests and admin tooling). */
  public String codeAt(String base32Secret, Instant now) {
    byte[] key = base32Decode(base32Secret.trim().toUpperCase(Locale.ROOT));
    return codeFor(key, now.getEpochSecond() / STEP_SECONDS);
  }

  private String codeFor(byte[] key, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int offset = hash[hash.length - 1] & 0x0F;
      int binary = ((hash[offset] & 0x7F) << 24)
          | ((hash[offset + 1] & 0xFF) << 16)
          | ((hash[offset + 2] & 0xFF) << 8)
          | (hash[offset + 3] & 0xFF);
      int otp = binary % 1_000_000;
      return String.format(Locale.ROOT, "%06d", otp);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA1 unavailable", e);
    }
  }

  static String base32Encode(byte[] bytes) {
    StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        bitsLeft -= 5;
        out.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
      }
    }
    if (bitsLeft > 0) {
      out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
    }
    return out.toString();
  }

  static byte[] base32Decode(String encoded) {
    String s = encoded.replace("=", "").replaceAll("\\s", "");
    ByteBufferCollector collector = new ByteBufferCollector();
    int buffer = 0;
    int bitsLeft = 0;
    for (int i = 0; i < s.length(); i++) {
      int val = BASE32_ALPHABET.indexOf(s.charAt(i));
      if (val < 0) {
        throw new IllegalArgumentException("Invalid Base32 character: " + s.charAt(i));
      }
      buffer = (buffer << 5) | val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        bitsLeft -= 8;
        collector.add((byte) ((buffer >> bitsLeft) & 0xFF));
      }
    }
    return collector.toArray();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static final class ByteBufferCollector {
    private byte[] buf = new byte[32];
    private int size;

    void add(byte b) {
      if (size == buf.length) {
        byte[] grown = new byte[buf.length * 2];
        System.arraycopy(buf, 0, grown, 0, size);
        buf = grown;
      }
      buf[size++] = b;
    }

    byte[] toArray() {
      byte[] result = new byte[size];
      System.arraycopy(buf, 0, result, 0, size);
      return result;
    }
  }
}
