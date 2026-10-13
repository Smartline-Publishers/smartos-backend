package com.smartline.smartos.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * One-way hashes for stored secrets (invite tokens, refresh tokens).
 * Raw values never touch the database.
 */
public final class TokenHashes {

  private TokenHashes() {
  }

  public static String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
