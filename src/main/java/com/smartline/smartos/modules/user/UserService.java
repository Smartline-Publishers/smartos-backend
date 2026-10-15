package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.SessionResponse;
import com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.security.Role;
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

  public UserService(AppUserRepository users, OrganizationRepository organizations,
      PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokens,
      com.smartline.smartos.media.MediaService media) {
    this.users = users;
    this.organizations = organizations;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokens = refreshTokens;
    this.media = media;
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
    if (role == Role.PLATFORM_ADMIN && !callerIsPlatformAdmin()) {
      throw ApiException.forbidden("Only platform admins can grant the platform admin role");
    }
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
    return users.save(user);
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
      if (req.getRole() == Role.PLATFORM_ADMIN && !callerIsPlatformAdmin()) {
        throw ApiException.forbidden("Only platform admins can grant the platform admin role");
      }
      user.setRole(req.getRole());
    }
    if (req.getActive() != null) {
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
  public void changePassword(ChangePasswordRequest req) {
    AppUser user = me();
    if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
      throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
          "Current password is incorrect");
    }
    if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest("New password must differ from the current password");
    }
    user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
    users.save(user);
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

  private UserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
  }
}
