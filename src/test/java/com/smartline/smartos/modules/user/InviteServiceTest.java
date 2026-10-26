package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.InviteDtos.AcceptInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.CreateInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.InviteResponse;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.tenant.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

  @Mock private InviteRepository invites;
  @Mock private AppUserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private OnboardingProgressRepository onboardingProgress;

  private InviteService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new InviteService(invites, users, organizations, refreshTokens, passwordEncoder,
        jwtService, onboardingProgress);
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
  }

  @Test
  void createReturnsPlaintextTokenOnceAndStoresHash() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(invites.save(any(Invite.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateInviteRequest req = new CreateInviteRequest();
    req.setEmail("editor@acme.test");
    req.setRole(Role.EDITOR);
    InviteResponse created = service.create(req);

    assertNotNull(created.getToken());
    ArgumentCaptor<Invite> saved = ArgumentCaptor.forClass(Invite.class);
    verify(invites).save(saved.capture());
    assertEquals(TokenHashes.sha256Hex(created.getToken()), saved.getValue().getTokenHash());
    assertEquals("editor@acme.test", saved.getValue().getEmail());
  }

  @Test
  void platformAdminInviteIsRejected() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));

    CreateInviteRequest req = new CreateInviteRequest();
    req.setEmail("root@acme.test");
    req.setRole(Role.PLATFORM_ADMIN);

    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.create(req)).getStatus());
  }

  @Test
  void acceptCreatesUserAndTokens() {
    Invite invite = pendingInvite();
    when(invites.findByTokenHash(eq(TokenHashes.sha256Hex("plain-token"))))
        .thenReturn(Optional.of(invite));
    when(users.existsByOrganization_IdAndEmail(eq(orgId), eq("editor@acme.test")))
        .thenReturn(false);
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(1L);
    when(passwordEncoder.encode("secret123")).thenReturn("hash");
    when(users.save(any(AppUser.class))).thenAnswer(inv -> {
      AppUser u = inv.getArgument(0);
      u.setId(UUID.randomUUID());
      return u;
    });
    when(jwtService.generateAccessToken(any(), eq(orgId), any())).thenReturn("access");
    when(jwtService.generateRefreshToken(any(), eq(orgId))).thenReturn("refresh");

    AcceptInviteRequest req = new AcceptInviteRequest();
    req.setToken("plain-token");
    req.setFullName("Ed Editor");
    req.setPassword("secret123");
    TokenResponse tokens = service.accept(req);

    assertEquals("access", tokens.getAccessToken());
    assertNotNull(invite.getAcceptedAt());
    verify(users).save(any(AppUser.class));
    verify(refreshTokens).save(any(RefreshToken.class));
  }

  @Test
  void acceptToleratesDuplicateRefreshStore() {
    Invite invite = pendingInvite();
    when(invites.findByTokenHash(eq(TokenHashes.sha256Hex("plain-token"))))
        .thenReturn(Optional.of(invite));
    when(users.existsByOrganization_IdAndEmail(eq(orgId), eq("editor@acme.test")))
        .thenReturn(false);
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(1L);
    when(passwordEncoder.encode("secret123")).thenReturn("hash");
    when(users.save(any(AppUser.class))).thenAnswer(inv -> {
      AppUser u = inv.getArgument(0);
      u.setId(UUID.randomUUID());
      return u;
    });
    when(jwtService.generateAccessToken(any(), eq(orgId), any())).thenReturn("access");
    when(jwtService.generateRefreshToken(any(), eq(orgId))).thenReturn("refresh");
    RefreshToken existing = new RefreshToken();
    existing.setId(UUID.randomUUID());
    when(refreshTokens.save(any(RefreshToken.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
    when(refreshTokens.findByTokenHash(any(String.class))).thenReturn(Optional.of(existing));

    AcceptInviteRequest req = new AcceptInviteRequest();
    req.setToken("plain-token");
    req.setFullName("Ed Editor");
    req.setPassword("secret123");
    TokenResponse tokens = service.accept(req);

    assertEquals("access", tokens.getAccessToken());
  }

  @Test
  void acceptUnknownTokenReturns404() {
    when(invites.findByTokenHash(eq(TokenHashes.sha256Hex("nope"))))
        .thenReturn(Optional.empty());

    AcceptInviteRequest req = new AcceptInviteRequest();
    req.setToken("nope");
    req.setFullName("Ed Editor");
    req.setPassword("secret123");

    assertEquals(HttpStatus.NOT_FOUND,
        assertThrows(ApiException.class, () -> service.accept(req)).getStatus());
  }

  @Test
  void acceptOverQuotaReturns403PlanLimit() {
    Invite invite = pendingInvite();
    when(invites.findByTokenHash(eq(TokenHashes.sha256Hex("plain-token"))))
        .thenReturn(Optional.of(invite));
    when(users.existsByOrganization_IdAndEmail(eq(orgId), eq("editor@acme.test")))
        .thenReturn(false);
    when(users.countByOrganization_Id(eq(orgId))).thenReturn(5L);

    AcceptInviteRequest req = new AcceptInviteRequest();
    req.setToken("plain-token");
    req.setFullName("Ed Editor");
    req.setPassword("secret123");

    ApiException ex = assertThrows(ApiException.class, () -> service.accept(req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    assertEquals("PLAN_LIMIT", ex.getCode());
  }

  private Invite pendingInvite() {
    Invite invite = new Invite();
    invite.setId(UUID.randomUUID());
    invite.setOrganization(org);
    invite.setEmail("editor@acme.test");
    invite.setRole(Role.EDITOR);
    invite.setTokenHash(TokenHashes.sha256Hex("plain-token"));
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    return invite;
  }
}
