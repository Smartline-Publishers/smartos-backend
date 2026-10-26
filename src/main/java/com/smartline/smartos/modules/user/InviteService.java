package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.legal.LegalAcceptance.Context;
import com.smartline.smartos.modules.legal.LegalService;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.InviteDtos.AcceptInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.CreateInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.InviteResponse;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.RolePermissions;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

  private final InviteRepository invites;
  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final OnboardingProgressRepository onboardingProgress;
  private final LegalService legalService;
  private final SecureRandom random = new SecureRandom();

  public InviteService(InviteRepository invites, AppUserRepository users,
      OrganizationRepository organizations, RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder, JwtService jwtService,
      OnboardingProgressRepository onboardingProgress, LegalService legalService) {
    this.invites = invites;
    this.users = users;
    this.organizations = organizations;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.onboardingProgress = onboardingProgress;
    this.legalService = legalService;
  }

  @Transactional
  @Audited(action = "invite.create", entityType = "Invite")
  public InviteResponse create(CreateInviteRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Role role = req.getRole() != null ? req.getRole() : Role.VIEWER;
    if (role == Role.PLATFORM_ADMIN) {
      throw ApiException.badRequest("Platform admin cannot be granted by invitation");
    }
    if (!RolePermissions.isAssignableByOrgAdmin(role)) {
      throw ApiException.forbidden("You cannot invite a user with the " + role.name() + " role");
    }
    String token = generateToken();
    Invite invite = new Invite();
    invite.setOrganization(org);
    invite.setEmail(req.getEmail().trim().toLowerCase());
    invite.setRole(role);
    invite.setTokenHash(TokenHashes.sha256Hex(token));
    invite.setExpiresAt(Instant.now().plus(req.getDaysValid() != null ? req.getDaysValid() : 7,
        ChronoUnit.DAYS));
    invite.setCreatedBy(currentActorId());
    Invite saved = invites.save(invite);
    return toResponse(saved, token);
  }

  @Transactional(readOnly = true)
  public List<InviteResponse> pending() {
    UUID orgId = TenantContext.require();
    return invites.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
        .filter(i -> i.getAcceptedAt() == null && i.getExpiresAt().isAfter(Instant.now()))
        .map(i -> toResponse(i, null))
        .toList();
  }

  /** Used by internal callers/tests that do not have a request context. */
  @Transactional
  public TokenResponse accept(AcceptInviteRequest req) {
    return accept(req, null, null);
  }

  @Transactional
  public TokenResponse accept(AcceptInviteRequest req, String ipAddress, String userAgent) {
    Invite invite = invites.findByTokenHash(TokenHashes.sha256Hex(req.getToken().trim()))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Invite invalid or expired"));
    if (invite.getAcceptedAt() != null || invite.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invite invalid or expired");
    }
    Organization org = invite.getOrganization();
    UUID orgId = org.getId();
    if (users.existsByOrganization_IdAndEmail(orgId, invite.getEmail())) {
      throw ApiException.conflict("User email already exists in organization");
    }
    if (users.countByOrganization_Id(orgId) >= org.getPlan().getMaxUsers()) {
      throw ApiException.planLimit("Organization user quota reached for plan "
          + org.getPlan().name());
    }
    AppUser user = new AppUser();
    user.setOrganization(org);
    user.setEmail(invite.getEmail());
    user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
    user.setFullName(req.getFullName().trim());
    user.setRole(invite.getRole());
    user.setActive(true);
    AppUser saved = users.save(user);

    // Backend enforcement: no account joins the organisation without an
    // explicit acceptance of the current terms and privacy policy.
    legalService.requireAndRecord(saved.getId(), orgId, req.getTermsVersion(),
        req.getPrivacyVersion(), Context.INVITE_ACCEPTANCE, invite.getId(), ipAddress, userAgent);

    // The invited user gets their own independent onboarding checklist, linked to them alone.
    OnboardingProgress p = new OnboardingProgress();
    p.setUserId(saved.getId());
    p.setUser(saved);
    p.setOrganization(org);
    onboardingProgress.save(p);
    invite.setAcceptedAt(Instant.now());
    invites.save(invite);
    List<String> roles = List.of(saved.getRole().name());
    String access = jwtService.generateAccessToken(saved.getId(), orgId, roles);
    String refresh = jwtService.generateRefreshToken(saved.getId(), orgId);
    storeRefresh(saved, refresh);
    return new TokenResponse(access, refresh, "Bearer");
  }

  @Transactional
  @Audited(action = "invite.revoke", entityType = "Invite")
  public void revoke(UUID id) {
    UUID orgId = TenantContext.require();
    Invite invite = invites.findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> ApiException.notFound("Invite", id));
    invites.delete(invite);
  }

  private void storeRefresh(AppUser user, String refreshToken) {
    String hash = TokenHashes.sha256Hex(refreshToken);
    RefreshToken record = new RefreshToken();
    record.setUser(user);
    record.setTokenHash(hash);
    record.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiryMs()));
    try {
      refreshTokens.save(record);
    } catch (DataIntegrityViolationException e) {
      // Concurrent duplicate submission: the row already exists.
      refreshTokens.findByTokenHash(hash).orElseThrow(() -> e);
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private UUID currentActorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    return null;
  }

  private InviteResponse toResponse(Invite invite, String token) {
    InviteResponse r = new InviteResponse();
    r.setId(invite.getId());
    r.setEmail(invite.getEmail());
    r.setRole(invite.getRole());
    r.setExpiresAt(invite.getExpiresAt());
    r.setAcceptedAt(invite.getAcceptedAt());
    r.setToken(token);
    return r;
  }
}
