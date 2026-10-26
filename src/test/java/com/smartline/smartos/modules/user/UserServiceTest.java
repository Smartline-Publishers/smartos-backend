package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private AppUserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private com.smartline.smartos.media.MediaService media;
  @Mock private OnboardingProgressRepository onboardingProgress;
  @Mock private JwtService jwtService;

  private UserService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new UserService(users, organizations, passwordEncoder, refreshTokens, media,
        onboardingProgress, jwtService);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    org.setPlan(Organization.Plan.FREE);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void createBeyondQuotaReturns403PlanLimit() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(5L);

    CreateUserRequest req = new CreateUserRequest();
    req.setEmail("extra@acme.test");
    req.setPassword("secret123");
    req.setFullName("Extra User");
    req.setRole(Role.VIEWER);

    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    assertEquals("PLAN_LIMIT", ex.getCode());
    verify(users, never()).save(any());
  }

  @Test
  void createWithinQuotaSucceeds() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(2L);
    when(users.existsByOrganization_IdAndEmail(eq(orgId), eq("new@acme.test")))
        .thenReturn(false);
    when(passwordEncoder.encode("secret123")).thenReturn("hash");
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateUserRequest req = new CreateUserRequest();
    req.setEmail("new@acme.test");
    req.setPassword("secret123");
    req.setFullName("New User");
    req.setRole(Role.EDITOR);

    AppUser saved = service.create(req);

    assertEquals("new@acme.test", saved.getEmail());
  }

  @Test
  void orgAdminCannotGrantPlatformAdmin() {
    authenticate(Role.ORG_ADMIN);
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(1L);

    CreateUserRequest req = new CreateUserRequest();
    req.setEmail("root@acme.test");
    req.setPassword("secret123");
    req.setFullName("Root User");
    req.setRole(Role.PLATFORM_ADMIN);

    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    verify(users, never()).save(any());
  }

  @Test
  void orgAdminCanAssignOrganizationScopedRoles() {
    authenticate(Role.ORG_ADMIN);
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(1L);
    when(users.existsByOrganization_IdAndEmail(eq(orgId), eq("ed@acme.test"))).thenReturn(false);
    when(passwordEncoder.encode("secret123")).thenReturn("hash");
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateUserRequest req = new CreateUserRequest();
    req.setEmail("ed@acme.test");
    req.setPassword("secret123");
    req.setFullName("Ed Editor");
    req.setRole(Role.EDITOR);

    AppUser saved = service.create(req);

    assertEquals(Role.EDITOR, saved.getRole());
  }

  @Test
  void roleChangeIsRejectedForRolesOutsideTheOrgAdminRemit() {
    authenticate(Role.ORG_ADMIN);
    UUID targetId = UUID.randomUUID();
    AppUser target = user(targetId, "ed@acme.test");
    when(users.findByIdAndOrganization_Id(eq(targetId), eq(orgId))).thenReturn(Optional.of(target));

    UpdateUserRequest req = new UpdateUserRequest();
    req.setRole(Role.PLATFORM_ADMIN);

    ApiException ex = assertThrows(ApiException.class, () -> service.update(targetId, req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
  }

  @Test
  void orgAdminCanChangeAnotherUsersRole() {
    authenticate(Role.ORG_ADMIN);
    UUID targetId = UUID.randomUUID();
    AppUser target = user(targetId, "ed@acme.test");
    when(users.findByIdAndOrganization_Id(eq(targetId), eq(orgId))).thenReturn(Optional.of(target));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateUserRequest req = new UpdateUserRequest();
    req.setRole(Role.FINANCE);

    AppUser updated = service.update(targetId, req);

    assertEquals(Role.FINANCE, updated.getRole());
  }

  @Test
  void orgAdminCannotChangeTheirOwnRole() {
    UUID selfId = UUID.randomUUID();
    authenticateAs(selfId, Role.ORG_ADMIN);
    AppUser self = user(selfId, "admin@acme.test");
    self.setRole(Role.ORG_ADMIN);
    when(users.findByIdAndOrganization_Id(eq(selfId), eq(orgId))).thenReturn(Optional.of(self));

    UpdateUserRequest req = new UpdateUserRequest();
    req.setRole(Role.VIEWER);

    ApiException ex = assertThrows(ApiException.class, () -> service.update(selfId, req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
  }

  @Test
  void orgAdminCannotDeactivateTheirOwnAccount() {
    UUID selfId = UUID.randomUUID();
    authenticateAs(selfId, Role.ORG_ADMIN);
    AppUser self = user(selfId, "admin@acme.test");
    when(users.findByIdAndOrganization_Id(eq(selfId), eq(orgId))).thenReturn(Optional.of(self));

    UpdateUserRequest req = new UpdateUserRequest();
    req.setActive(false);

    ApiException ex = assertThrows(ApiException.class, () -> service.update(selfId, req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
  }

  @Test
  void meReturnsScopedCaller() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.EDITOR);
    AppUser user = user(userId, "ed@acme.test");
    when(users.findById(userId)).thenReturn(Optional.of(user));

    assertEquals(user, service.me());
  }

  @Test
  void updateProfileTrimsNameAndSetsAvatar() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser user = user(userId, "v@acme.test");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest req =
        new com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest();
    req.setFullName("  New Name ");
    req.setAvatarUrl("https://cdn.acme.test/me.png");

    AppUser updated = service.updateProfile(req);

    assertEquals("New Name", updated.getFullName());
    assertEquals("https://cdn.acme.test/me.png", updated.getAvatarUrl());
  }

  @Test
  void updateProfileClearAvatarAndRejectsBadUrl() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser user = user(userId, "v@acme.test");
    user.setAvatarUrl("https://cdn.acme.test/me.png");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest clear =
        new com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest();
    clear.setClearAvatar(true);
    assertEquals(null, service.updateProfile(clear).getAvatarUrl());

    com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest bad =
        new com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest();
    bad.setAvatarUrl("ftp://acme.test/me.png");
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.updateProfile(bad)).getStatus());
  }

  @Test
  void changePasswordChecksCurrentAndDiffers() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser user = user(userId, "v@acme.test");
    user.setPasswordHash("old-hash");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

    com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest bad =
        new com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest();
    bad.setCurrentPassword("wrong");
    bad.setNewPassword("new-secret-1");
    assertEquals(HttpStatus.UNAUTHORIZED,
        assertThrows(ApiException.class, () -> service.changePassword(bad, null)).getStatus());

    when(passwordEncoder.matches("old-secret", "old-hash")).thenReturn(true);
    when(passwordEncoder.matches("old-secret-new", "old-hash")).thenReturn(false);
    when(passwordEncoder.encode("old-secret-new")).thenReturn("new-hash");

    com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest ok =
        new com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest();
    ok.setCurrentPassword("old-secret");
    ok.setNewPassword("old-secret-new");
    when(refreshTokens.findByUserId(userId)).thenReturn(List.of());
    service.changePassword(ok, null);
    assertEquals("new-hash", user.getPasswordHash());
  }

  @Test
  void sessionsListActiveOnlyAndRevokeBlocksCurrent() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser owner = user(userId, "v@acme.test");
    when(users.findById(userId)).thenReturn(Optional.of(owner));
    RefreshToken current = session(owner, "current-raw", 3600);
    RefreshToken expired = session(owner, "old-raw", -10);
    RefreshToken other = session(owner, "other-raw", 3600);
    when(refreshTokens.findByUserId(userId)).thenReturn(List.of(current, expired, other));

    List<com.smartline.smartos.modules.user.UserDtos.SessionResponse> sessions =
        service.sessions("current-raw");

    assertEquals(2, sessions.size());
    assertEquals(1, sessions.stream().filter(
        com.smartline.smartos.modules.user.UserDtos.SessionResponse::isCurrent).count());

    when(refreshTokens.findById(other.getId())).thenReturn(Optional.of(other));
    service.revokeSession(other.getId(), "current-raw");
    assertEquals(true, other.isRevoked());

    when(refreshTokens.findById(current.getId())).thenReturn(Optional.of(current));
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class,
            () -> service.revokeSession(current.getId(), "current-raw")).getStatus());
  }

  @Test
  void revokeOtherSessionsKeepsCurrentAndSkipsInactive() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser owner = user(userId, "v@acme.test");
    when(users.findById(userId)).thenReturn(Optional.of(owner));
    RefreshToken current = session(owner, "current-raw", 3600);
    RefreshToken other = session(owner, "other-raw", 3600);
    RefreshToken expired = session(owner, "old-raw", -10);
    RefreshToken revoked = session(owner, "revoked-raw", 3600);
    revoked.setRevoked(true);
    when(refreshTokens.findByUserId(userId))
        .thenReturn(List.of(current, other, expired, revoked));

    service.revokeOtherSessions("current-raw");

    assertFalse(current.isRevoked());
    assertTrue(other.isRevoked());
    assertFalse(expired.isRevoked());
    assertTrue(revoked.isRevoked());
    verify(refreshTokens).saveAll(List.of(other));
  }

  @Test
  void revokeOtherSessionsRequiresCurrentToken() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);

    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
        () -> service.revokeOtherSessions(" ")).getStatus());
    verify(refreshTokens, never()).findByUserId(any());
  }

  private AppUser user(UUID id, String email) {
    AppUser user = new AppUser();
    user.setId(id);
    user.setOrganization(org);
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setFullName("Test User");
    user.setRole(Role.VIEWER);
    user.setActive(true);
    return user;
  }

  private RefreshToken session(AppUser owner, String raw, long ttlSeconds) {
    RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setUser(owner);
    t.setTokenHash(com.smartline.smartos.security.TokenHashes.sha256Hex(raw));
    t.setExpiresAt(java.time.Instant.now().plusSeconds(ttlSeconds));
    t.setRevoked(false);
    return t;
  }

  private void authenticateAs(UUID userId, Role role) {
    UserPrincipal principal = new UserPrincipal(userId, orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  @Test
  void uploadAvatarStoresHostedUrl() {
    UUID userId = UUID.randomUUID();
    authenticateAs(userId, Role.VIEWER);
    AppUser user = user(userId, "v@acme.test");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile("file", "me.png", "image/png",
            new byte[] {1, 2, 3});
    when(media.replace(eq("https://cdn.acme.test/old.png"), eq(file), any(String.class)))
        .thenReturn("https://res.cloudinary.com/demo/image/upload/v1/me.png");
    user.setAvatarUrl("https://cdn.acme.test/old.png");

    AppUser updated = service.uploadAvatar(file);

    assertEquals("https://res.cloudinary.com/demo/image/upload/v1/me.png",
        updated.getAvatarUrl());
  }

  private void authenticate(Role role) {
    UserPrincipal principal =
        new UserPrincipal(UUID.randomUUID(), orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }
}
