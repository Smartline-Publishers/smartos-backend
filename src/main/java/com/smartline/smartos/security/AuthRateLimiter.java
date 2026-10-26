package com.smartline.smartos.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Small in-process attempt limiter for credential endpoints.
 *
 * <p>Keys are salted hashes of {@code email + client IP}, so raw identifiers
 * are not retained in memory. This is deliberately simple and single-node: if
 * the API runs behind multiple instances, put a shared limiter (Redis) in
 * front of these endpoints as described in docs/AUTHENTICATION.md.
 */
@Component
public class AuthRateLimiter {

  private static final int MAX_ENTRIES = 20_000;
  private static final String DUMMY_SALT = "smartos-rate-limit";

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final int loginMaxFailures;
  private final Duration loginWindow;
  private final int forgotMaxRequests;
  private final Duration forgotWindow;
  private final int passkeyMaxFailures;
  private final Duration passkeyWindow;

  public AuthRateLimiter(
      @Value("${app.security.rate-limit.login-max-failures:8}") int loginMaxFailures,
      @Value("${app.security.rate-limit.login-window-seconds:600}") long loginWindowSeconds,
      @Value("${app.security.rate-limit.forgot-max-requests:4}") int forgotMaxRequests,
      @Value("${app.security.rate-limit.forgot-window-seconds:1800}") long forgotWindowSeconds,
      @Value("${app.security.rate-limit.passkey-max-failures:10}") int passkeyMaxFailures,
      @Value("${app.security.rate-limit.passkey-window-seconds:600}") long passkeyWindowSeconds) {
    this.loginMaxFailures = loginMaxFailures;
    this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
    this.forgotMaxRequests = forgotMaxRequests;
    this.forgotWindow = Duration.ofSeconds(forgotWindowSeconds);
    this.passkeyMaxFailures = passkeyMaxFailures;
    this.passkeyWindow = Duration.ofSeconds(passkeyWindowSeconds);
  }

  public boolean loginAllowed(String email, String clientIp) {
    return !isLimited(key("login", email, clientIp), loginMaxFailures);
  }

  public void recordLoginFailure(String email, String clientIp) {
    increment(key("login", email, clientIp), loginWindow);
  }

  public void clearLoginFailures(String email, String clientIp) {
    buckets.remove(key("login", email, clientIp));
  }

  public boolean forgotAllowed(String email, String clientIp) {
    String key = key("forgot", email, clientIp);
    return incrementIfBelow(key, forgotMaxRequests, forgotWindow);
  }

  public void recordPasskeyFailure(String clientIp) {
    increment(key("passkey", "", clientIp), passkeyWindow);
  }

  public boolean passkeyAllowed(String clientIp) {
    return !isLimited(key("passkey", "", clientIp), passkeyMaxFailures);
  }

  private boolean isLimited(String key, int max) {
    Bucket bucket = buckets.get(key);
    if (bucket == null) {
      return false;
    }
    synchronized (bucket) {
      if (Instant.now().isAfter(bucket.resetAt)) {
        buckets.remove(key, bucket);
        return false;
      }
      return bucket.count.get() >= max;
    }
  }

  private void increment(String key, Duration window) {
    pruneIfNeeded();
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(Instant.now().plus(window)));
    synchronized (bucket) {
      if (Instant.now().isAfter(bucket.resetAt)) {
        bucket.resetAt = Instant.now().plus(window);
        bucket.count.set(0);
      }
      bucket.count.incrementAndGet();
    }
  }

  /** For endpoints where every request counts (e.g. reset emails). */
  private boolean incrementIfBelow(String key, int max, Duration window) {
    pruneIfNeeded();
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(Instant.now().plus(window)));
    synchronized (bucket) {
      if (Instant.now().isAfter(bucket.resetAt)) {
        bucket.resetAt = Instant.now().plus(window);
        bucket.count.set(0);
      }
      if (bucket.count.get() >= max) {
        return false;
      }
      bucket.count.incrementAndGet();
      return true;
    }
  }

  private void pruneIfNeeded() {
    if (buckets.size() < MAX_ENTRIES) {
      return;
    }
    Instant now = Instant.now();
    buckets.entrySet().removeIf(e -> now.isAfter(e.getValue().resetAt));
  }

  private static String key(String scope, String email, String clientIp) {
    String identifier = (email == null ? "" : email.trim().toLowerCase())
        + "|" + (clientIp == null ? "unknown" : clientIp);
    return scope + ":" + sha256(identifier + "|" + DUMMY_SALT);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static final class Bucket {
    private final AtomicInteger count = new AtomicInteger();
    private Instant resetAt;

    private Bucket(Instant resetAt) {
      this.resetAt = resetAt;
    }
  }
}
