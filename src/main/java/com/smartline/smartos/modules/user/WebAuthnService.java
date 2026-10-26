package com.smartline.smartos.modules.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.user.PasskeyDtos.AssertionRequest;
import com.smartline.smartos.modules.user.PasskeyDtos.PasskeyResponse;
import com.smartline.smartos.modules.user.PasskeyDtos.RegisterRequest;
import com.smartline.smartos.modules.user.webauthn.CosePublicKey;
import com.smartline.smartos.modules.user.webauthn.WebAuthnException;
import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier;
import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier.AssertionResult;
import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier.Base64Url;
import com.smartline.smartos.modules.user.webauthn.WebAuthnVerifier.RegisteredCredential;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.AuthRateLimiter;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.security.UserPrincipal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WebAuthn/passkey registration, management and passwordless sign-in.
 *
 * <p>All cryptographic checks (challenge, RP ID, origin, flags, signature,
 * counter) are performed server-side by {@link WebAuthnVerifier}. Challenges
 * are random, short-lived and single-use.
 */
@Service
public class WebAuthnService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_PASSKEYS = 20;

  private final AppUserRepository users;
  private final PasskeyCredentialRepository credentials;
  private final WebAuthnChallengeRepository challenges;
  private final RefreshTokenRepository refreshTokens;
  private final JwtService jwtService;
  private final AuthRateLimiter rateLimiter;
  private final String rpId;
  private final String rpName;
  private final Set<String> allowedOrigins;
  private final String userVerification;
  private final long timeoutMs;
  private final long challengeTtlSeconds;

  public WebAuthnService(AppUserRepository users, PasskeyCredentialRepository credentials,
      WebAuthnChallengeRepository challenges, RefreshTokenRepository refreshTokens,
      JwtService jwtService, AuthRateLimiter rateLimiter,
      @Value("${app.webauthn.rp-id:localhost}") String rpId,
      @Value("${app.webauthn.rp-name:SmartOS}") String rpName,
      @Value("${app.webauthn.allowed-origins:http://localhost:3000}") String allowedOrigins,
      @Value("${app.webauthn.user-verification:preferred}") String userVerification,
      @Value("${app.webauthn.timeout-ms:60000}") long timeoutMs,
      @Value("${app.webauthn.challenge-ttl-seconds:300}") long challengeTtlSeconds) {
    this.users = users;
    this.credentials = credentials;
    this.challenges = challenges;
    this.refreshTokens = refreshTokens;
    this.jwtService = jwtService;
    this.rateLimiter = rateLimiter;
    this.rpId = rpId;
    this.rpName = rpName;
    this.allowedOrigins = split(allowedOrigins);
    this.userVerification = userVerification == null ? "preferred" : userVerification.toLowerCase(Locale.ROOT);
    this.timeoutMs = timeoutMs;
    this.challengeTtlSeconds = challengeTtlSeconds;
  }

  // ---- Registration / management (authenticated) ----

  @Transactional
  public Map<String, Object> registrationOptions() {
    AppUser user = currentUser();
    String challenge = newChallenge(WebAuthnChallenge.Purpose.REGISTRATION, user);

    List<PasskeyCredential> existing = credentials.findByUserIdOrderByCreatedAtAsc(user.getId());
    List<Map<String, Object>> exclude = existing.stream()
        .map(c -> descriptor(c.getCredentialId(), c.getTransports()))
        .toList();

    Map<String, Object> options = new LinkedHashMap<>();
    options.put("challenge", challenge);
    options.put("rp", Map.of("id", rpId, "name", rpName));
    options.put("user", Map.of(
        "id", Base64Url.encode(uuidBytes(user.getId())),
        "name", user.getEmail(),
        "displayName", user.getFullName()));
    options.put("pubKeyCredParams", List.of(
        Map.of("type", "public-key", "alg", -7),
        Map.of("type", "public-key", "alg", -257),
        Map.of("type", "public-key", "alg", -8)));
    options.put("timeout", timeoutMs);
    options.put("excludeCredentials", exclude);
    options.put("authenticatorSelection", Map.of(
        "residentKey", "preferred",
        "requireResidentKey", false,
        "userVerification", userVerification));
    options.put("attestation", "none");
    options.put("extensions", Map.of("credProps", true));
    return options;
  }

  @Transactional
  public PasskeyResponse register(RegisterRequest req) {
    AppUser user = currentUser();
    String challenge = extractChallenge(req.getClientDataJSON());
    WebAuthnChallenge stored = requireChallenge(challenge,
        WebAuthnChallenge.Purpose.REGISTRATION);
    if (stored.getUser() == null || !stored.getUser().getId().equals(user.getId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WEBAUTHN_FAILED", "Invalid registration challenge");
    }
    if (credentials.countByUserId(user.getId()) >= MAX_PASSKEYS) {
      throw ApiException.badRequest("You have reached the maximum number of passkeys (" + MAX_PASSKEYS + ")");
    }

    RegisteredCredential registered;
    try {
      registered = WebAuthnVerifier.verifyRegistration(
          Base64Url.decode(req.getClientDataJSON()),
          Base64Url.decode(req.getAttestationObject()),
          rpId, allowedOrigins);
    } catch (WebAuthnException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WEBAUTHN_FAILED",
          "Could not verify this passkey: " + e.getMessage());
    }

    if (!registered.credentialId().equals(req.getCredentialId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WEBAUTHN_FAILED",
          "Credential id does not match the attestation");
    }
    if (credentials.existsByCredentialId(registered.credentialId())) {
      throw new ApiException(HttpStatus.CONFLICT, "CONFLICT",
          "This passkey is already registered");
    }

    PasskeyCredential credential = new PasskeyCredential();
    credential.setUser(user);
    credential.setCredentialId(registered.credentialId());
    credential.setPublicKeyCose(registered.publicKeyCose());
    credential.setSignCount(registered.signCount());
    credential.setAaguid(registered.aaguid());
    credential.setBackupEligible(registered.backupEligible());
    credential.setBackupState(registered.backupState());
    credential.setTransports(joinTransports(req.getTransports()));
    long count = credentials.countByUserId(user.getId());
    credential.setNickname(nickname(req.getNickname(), "Passkey " + (count + 1)));
    PasskeyCredential saved = credentials.save(credential);

    consume(stored);
    return PasskeyResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<PasskeyResponse> list() {
    AppUser user = currentUser();
    return credentials.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
        .map(PasskeyResponse::from)
        .toList();
  }

  @Transactional
  public PasskeyResponse rename(UUID id, String nickname) {
    AppUser user = currentUser();
    PasskeyCredential credential = credentials.findByIdAndUserId(id, user.getId())
        .orElseThrow(() -> ApiException.notFound("Passkey", id));
    credential.setNickname(nickname(nickname, credential.getNickname()));
    return PasskeyResponse.from(credentials.save(credential));
  }

  @Transactional
  public void delete(UUID id) {
    AppUser user = currentUser();
    PasskeyCredential credential = credentials.findByIdAndUserId(id, user.getId())
        .orElseThrow(() -> ApiException.notFound("Passkey", id));
    credentials.delete(credential);
  }

  // ---- Passwordless sign-in (public) ----

  @Transactional
  public Map<String, Object> authenticationOptions(String rawEmail) {
    String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    List<AppUser> matches = email.isBlank()
        ? List.of()
        : users.findByEmailIgnoreCase(email).stream().filter(AppUser::isActive).toList();

    AppUser boundUser = matches.size() == 1 ? matches.get(0) : null;
    String challenge = newChallenge(WebAuthnChallenge.Purpose.AUTHENTICATION, boundUser);

    List<Map<String, Object>> allow = boundUser == null
        ? List.of()
        : credentials.findByUserIdOrderByCreatedAtAsc(boundUser.getId()).stream()
            .map(c -> descriptor(c.getCredentialId(), c.getTransports()))
            .toList();

    Map<String, Object> options = new LinkedHashMap<>();
    options.put("challenge", challenge);
    options.put("rpId", rpId);
    options.put("timeout", timeoutMs);
    options.put("userVerification", userVerification);
    options.put("allowCredentials", allow);
    return options;
  }

  @Transactional
  public TokenResponse authenticate(AssertionRequest req, String clientIp) {
    if (!rateLimiter.passkeyAllowed(clientIp)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
          "Too many passkey attempts. Try again later.");
    }

    String challenge = extractChallenge(req.getClientDataJSON());
    WebAuthnChallenge stored = requireChallenge(challenge,
        WebAuthnChallenge.Purpose.AUTHENTICATION);
    PasskeyCredential credential = credentials.findByCredentialId(req.getCredentialId())
        .orElse(null);
    if (credential == null || credential.getUser() == null || !credential.getUser().isActive()) {
      throw passkeyFailed(clientIp);
    }
    if (stored.getUser() != null
        && !stored.getUser().getId().equals(credential.getUser().getId())) {
      throw passkeyFailed(clientIp);
    }

    AssertionResult result;
    try {
      result = WebAuthnVerifier.verifyAssertion(
          Base64Url.decode(req.getClientDataJSON()),
          Base64Url.decode(req.getAuthenticatorData()),
          Base64Url.decode(req.getSignature()),
          CosePublicKey.parse(credential.getPublicKeyCose()),
          credential.getSignCount(),
          rpId,
          allowedOrigins,
          "required".equals(userVerification));
    } catch (WebAuthnException e) {
      throw passkeyFailed(clientIp);
    }

    if (req.getUserHandle() != null && !req.getUserHandle().isBlank()) {
      byte[] handle;
      try {
        handle = Base64Url.decode(req.getUserHandle());
      } catch (WebAuthnException e) {
        throw passkeyFailed(clientIp);
      }
      if (!MessageDigest.isEqual(handle, uuidBytes(credential.getUser().getId()))) {
        throw passkeyFailed(clientIp);
      }
    }

    credential.setSignCount(result.signCount());
    credential.setLastUsedAt(Instant.now());
    credential.setBackupState(result.backupState());
    credentials.save(credential);
    consume(stored);

    AppUser user = credential.getUser();
    UUID orgId = user.getOrganization().getId();
    if (user.isTotpEnabled()) {
      // Passkeys do not silently bypass an existing second factor.
      return TokenResponse.challenge(jwtService.generateChallengeToken(user.getId(), orgId));
    }
    return issueTokens(user, orgId);
  }

  // ---- Helpers ----

  private AppUser currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "Authentication required"));
    if (!user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    return user;
  }

  private String newChallenge(WebAuthnChallenge.Purpose purpose, AppUser user) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = Base64Url.encode(bytes);
    WebAuthnChallenge challenge = new WebAuthnChallenge();
    challenge.setUser(user);
    challenge.setChallengeHash(TokenHashes.sha256Hex(raw));
    challenge.setPurpose(purpose);
    challenge.setExpiresAt(Instant.now().plusSeconds(challengeTtlSeconds));
    challenges.save(challenge);
    return raw;
  }

  private WebAuthnChallenge requireChallenge(String rawChallenge,
      WebAuthnChallenge.Purpose purpose) {
    WebAuthnChallenge challenge = challenges
        .findByChallengeHashForUpdate(TokenHashes.sha256Hex(rawChallenge))
        .orElseThrow(WebAuthnService::invalidChallenge);
    if (challenge.getPurpose() != purpose || !challenge.isUsable()) {
      throw invalidChallenge();
    }
    return challenge;
  }

  private static void consume(WebAuthnChallenge challenge) {
    challenge.setConsumedAt(Instant.now());
  }

  private static ApiException invalidChallenge() {
    return new ApiException(HttpStatus.BAD_REQUEST, "WEBAUTHN_FAILED",
        "This passkey challenge is invalid or has expired. Try again.");
  }

  private ApiException passkeyFailed(String clientIp) {
    rateLimiter.recordPasskeyFailure(clientIp);
    return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
        "Passkey sign-in failed. Try again or use your password.");
  }

  private TokenResponse issueTokens(AppUser user, UUID orgId) {
    List<String> roles = List.of(user.getRole().name());
    String access = jwtService.generateAccessToken(user.getId(), orgId, roles, user.getEmail(),
        user.getAuthVersion());
    String rawRefresh = jwtService.generateRefreshToken(user.getId(), orgId,
        user.getAuthVersion());
    RefreshToken record = new RefreshToken();
    record.setUser(user);
    record.setTokenHash(TokenHashes.sha256Hex(rawRefresh));
    record.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiryMs()));
    refreshTokens.saveAndFlush(record);
    return new TokenResponse(access, rawRefresh, "Bearer");
  }

  private static String extractChallenge(String clientDataJSON) {
    try {
      JsonNode node = JSON.readTree(Base64Url.decode(clientDataJSON));
      JsonNode value = node.get("challenge");
      if (value == null || !value.isTextual() || value.asText().isBlank()) {
        throw invalidChallenge();
      }
      return value.asText();
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw invalidChallenge();
    }
  }

  private static Map<String, Object> descriptor(String credentialId, String transports) {
    Map<String, Object> descriptor = new LinkedHashMap<>();
    descriptor.put("type", "public-key");
    descriptor.put("id", credentialId);
    if (transports != null && !transports.isBlank()) {
      descriptor.put("transports", List.of(transports.split(",")));
    }
    return descriptor;
  }

  private static String joinTransports(List<String> transports) {
    if (transports == null || transports.isEmpty()) {
      return null;
    }
    String joined = String.join(",", new LinkedHashSet<>(transports));
    return joined.length() > 255 ? joined.substring(0, 255) : joined;
  }

  private static String nickname(String requested, String fallback) {
    String value = requested == null ? "" : requested.trim();
    if (value.isEmpty()) {
      value = fallback == null ? "Passkey" : fallback;
    }
    return value.length() > 100 ? value.substring(0, 100) : value;
  }

  private static byte[] uuidBytes(UUID uuid) {
    return ByteBuffer.allocate(16)
        .putLong(uuid.getMostSignificantBits())
        .putLong(uuid.getLeastSignificantBits())
        .array();
  }

  private static Set<String> split(String value) {
    Set<String> result = new LinkedHashSet<>();
    if (value != null) {
      for (String part : value.split(",")) {
        if (!part.isBlank()) {
          result.add(part.trim());
        }
      }
    }
    return result;
  }
}
