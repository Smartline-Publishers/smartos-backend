package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.PasswordChangeResponse;
import com.smartline.smartos.modules.user.UserDtos.SessionResponse;
import com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.PasswordPolicy;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.RolePermissions;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenRepository refreshTokens;
  private final com.smartline.smartos.media.MediaService media;
  private final OnboardingProgressRepository onboardingProgress;
  private final JwtService jwtService;

  public UserService(AppUserRepository users, OrganizationRepository organizations,
      PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokens,
      com.smartline.smartos.media.MediaService media, OnboardingProgressRepository onboardingProgress,
      JwtService jwtService) {
    this.users = users;
    this.organizations = organizations;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokens = refreshTokens;
    this.media = media;
    this.onboardingProgress = onboardingProgress;
    this.jwtService = jwtService;
  }

  @Transactional
  @Audited(action = "user.create", entityType = "AppUser")
  public AppUser create(CreateUserRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    if (users.countByOrganization_Id(orgId) >= org.getPlan().getMaxUsers()) {
      throw ApiException.planLimit("Organization user quota reached for plan "
          + org.getPlan().name());
    }
    Role role = req.getRole() != null ? req.getRole() : Role.VIEWER;
    requireAssignableRole(role);
    String email = req.getEmail().trim().toLowerCase();
    if (users.existsByOrganization_IdAndEmail(orgId, email)) {
      throw ApiException.conflict("User email already exists in organization: " + email);
    }
    AppUser user = new AppUser();
    user.setOrganization(org);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
    user.setFullName(req.getFullName().trim());
    user.setRole(role);
    user.setActive(true);
    AppUser saved = users.save(user);
    // Every new user gets their own independent onboarding checklist, linked to them
    // alone and never shared with another user in the organization.
    OnboardingProgress p = new OnboardingProgress();
    p.setUserId(saved.getId());
    p.setUser(saved);
    p.setOrganization(org);
    onboardingProgress.save(p);
    return saved;
  }

  @Transactional(readOnly = true)
  public Page<AppUser> list(Pageable pageable) {
    UUID orgId = TenantContext.require();
    return users.findByOrganization_Id(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public AppUser get(UUID id) {
    UUID orgId = TenantContext.require();
    return users.findByIdAndOrganization_Id(id, orgId)
        .orElseThrow(() -> ApiException.notFound("User", id));
  }

  @Transactional
  @Audited(action = "user.update", entityType = "AppUser")
  public AppUser update(UUID id, UpdateUserRequest req) {
    AppUser user = get(id);
    if (req.getFullName() != null && !req.getFullName().isBlank()) {
      user.setFullName(req.getFullName().trim());
    }
    if (req.getRole() != null) {
      requireAssignableRole(req.getRole());
      if (req.getRole() != user.getRole() && isSelf(id)
          && !callerIsPlatformAdmin()) {
        // An org admin changing their own role would strand the organization
        // without an administrator; platform admins may still do so.
        throw ApiException.forbidden(
            "You cannot change your own role. Ask a platform administrator.");
      }
      user.setRole(req.getRole());
    }
    if (req.getActive() != null) {
      if (!req.getActive() && isSelf(id) && !callerIsPlatformAdmin()) {
        throw ApiException.forbidden("You cannot deactivate your own account");
      }
      user.setActive(req.getActive());
    }
    if (req.getAvatarUrl() != null) {
      user.setAvatarUrl(validatedAvatarUrl(req.getAvatarUrl()));
    }
    return users.save(user);
  }

  @Transactional(readOnly = true)
  public AppUser me() {
    UserPrincipal principal = currentPrincipal();
    if (principal == null) {
      throw ApiException.forbidden("Authentication required");
    }
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.notFound("User", principal.getUserId()));
    UUID orgId = TenantContext.current();
    if (orgId != null && user.getOrganization() != null
        && !orgId.equals(user.getOrganization().getId())) {
      throw ApiException.notFound("User", principal.getUserId());
    }
    return user;
  }

  @Transactional
  @Audited(action = "user.profile.update", entityType = "AppUser")
  public AppUser updateProfile(UpdateProfileRequest req) {
    AppUser user = me();
    if (req.getFullName() != null && !req.getFullName().isBlank()) {
      user.setFullName(req.getFullName().trim());
    }
    if (Boolean.TRUE.equals(req.getClearAvatar())) {
      user.setAvatarUrl(null);
    } else if (req.getAvatarUrl() != null) {
      user.setAvatarUrl(validatedAvatarUrl(req.getAvatarUrl()));
    }
    return users.save(user);
  }

  @Transactional
  public PasswordChangeResponse changePassword(ChangePasswordRequest req,
      String currentRefreshToken) {
    AppUser user = me();
    if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
      throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
          "Current password is incorrect");
    }
    if (req.getConfirmPassword() != null
        && !req.getConfirmPassword().equals(req.getNewPassword())) {
      throw ApiException.badRequest("The new passwords do not match");
    }
    PasswordPolicy.requireValid(req.getNewPassword(), user.getEmail());
    if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest("New password must differ from the current password");
    }

    Instant now = Instant.now();
    user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
    user.setLastPasswordChangeAt(now);
    // Invalidate all access/refresh tokens minted before this change.
    user.setAuthVersion(user.getAuthVersion() + 1);
    users.save(user);

    String currentHash = currentRefreshToken != null && !currentRefreshToken.isBlank()
        ? TokenHashes.sha256Hex(currentRefreshToken.trim())
        : null;
    List<RefreshToken> sessions = refreshTokens.findByUserId(user.getId());
    boolean hasCurrent = currentHash != null
        && sessions.stream().anyMatch(t -> !t.isRevoked() && currentHash.equals(t.getTokenHash()));
    sessions.stream().filter(t -> !t.isRevoked()).forEach(t -> t.setRevoked(true));
    refreshTokens.saveAll(sessions);

    boolean keepCurrent = req.isKeepCurrentSession() && hasCurrent;
    if (!keepCurrent) {
      return new PasswordChangeResponse();
    }

    // The old token pair is dead (auth_version changed), so keeping this
    // browser signed in means issuing a fresh pair carrying the new version.
    List<String> roles = List.of(user.getRole().name());
    String access = jwtService.generateAccessToken(user.getId(), user.getOrganization().getId(),
        roles, user.getEmail(), user.getAuthVersion());
    String rawRefresh = jwtService.generateRefreshToken(user.getId(),
        user.getOrganization().getId(), user.getAuthVersion());
    RefreshToken record = new RefreshToken();
    record.setUser(user);
    record.setTokenHash(TokenHashes.sha256Hex(rawRefresh));
    record.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiryMs()));
    refreshTokens.save(record);
    return new PasswordChangeResponse(access, rawRefresh);
  }

  @Transactional
  @Audited(action = "user.avatar.update", entityType = "AppUser")
  public AppUser uploadAvatar(MultipartFile file) {
    AppUser user = me();
    user.setAvatarUrl(media.replace(user.getAvatarUrl(), file, avatarFolder(user)));
    return users.save(user);
  }

  private String avatarFolder(AppUser user) {
    return "smartos/" + user.getOrganization().getId() + "/avatars";
  }

  @Transactional(readOnly = true)
  public List<SessionResponse> sessions(String currentRefreshToken) {
    AppUser user = me();
    String currentHash = currentRefreshToken != null && !currentRefreshToken.isBlank()
        ? TokenHashes.sha256Hex(currentRefreshToken.trim())
        : null;
    return refreshTokens.findByUserId(user.getId()).stream()
        .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
        .map(t -> new SessionResponse(t.getId(), t.getCreatedAt(), t.getExpiresAt(), false,
            currentHash != null && currentHash.equals(t.getTokenHash())))
        .toList();
  }

  @Transactional
  public void revokeSession(UUID sessionId, String currentRefreshToken) {
    AppUser user = me();
    RefreshToken session = refreshTokens.findById(sessionId)
        .filter(t -> t.getUser() != null && user.getId().equals(t.getUser().getId()))
        .orElseThrow(() -> ApiException.notFound("Session", sessionId));
    if (currentRefreshToken != null && !currentRefreshToken.isBlank()
        && TokenHashes.sha256Hex(currentRefreshToken.trim()).equals(session.getTokenHash())) {
      throw ApiException.badRequest("Use log out to end the current session");
    }
    session.setRevoked(true);
    refreshTokens.save(session);
  }

  @Transactional
  public void revokeOtherSessions(String currentRefreshToken) {
    if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
      throw ApiException.badRequest("Current session token is required");
    }
    AppUser user = me();
    String currentHash = TokenHashes.sha256Hex(currentRefreshToken.trim());
    Instant now = Instant.now();
    List<RefreshToken> others = refreshTokens.findByUserId(user.getId()).stream()
        .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(now))
        .filter(t -> !currentHash.equals(t.getTokenHash()))
        .toList();
    others.forEach(t -> t.setRevoked(true));
    refreshTokens.saveAll(others);
  }

  static String validatedAvatarUrl(String raw) {
    String url = raw.trim();
    if (url.isEmpty()) {
      throw ApiException.badRequest("Avatar URL must not be empty");
    }
    if (url.startsWith("data:image/")) {
      if (url.length() > 700_000 || !url.contains(";base64,")) {
        throw ApiException.badRequest("Avatar image is too large or not base64-encoded");
      }
      return url;
    }
    if ((url.startsWith("https://") || url.startsWith("http://")) && url.length() <= 2048
        && !url.contains(" ")) {
      return url;
    }
    throw ApiException.badRequest("Avatar must be an http(s) URL or a data:image/* URL");
  }

  @Transactional
  @Audited(action = "user.delete", entityType = "AppUser")
  public void delete(UUID id) {
    AppUser user = get(id);
    users.delete(user);
  }

  private boolean callerIsPlatformAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal
        && principal.getRoles().contains(Role.PLATFORM_ADMIN.name());
  }

  /**
   * Restricts which roles the caller may hand out.
   *
   * <p>A platform administrator may assign any role. An organization
   * administrator may assign any organization-scoped role but never
   * {@code PLATFORM_ADMIN}, which is a platform-wide privilege outside their
   * own organization's remit.
   */
  private void requireAssignableRole(Role role) {
    if (role == null) {
      throw ApiException.badRequest("A role is required");
    }
    if (callerIsPlatformAdmin()) {
      return;
    }
    if (!RolePermissions.isAssignableByOrgAdmin(role)) {
      throw ApiException.forbidden("You cannot grant the " + role.name() + " role");
    }
  }

  /** True when the given user is the caller making the request. */
  private boolean isSelf(UUID id) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal
        && principal.getUserId() != null && principal.getUserId().equals(id);
  }

  private UserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
  }
}
