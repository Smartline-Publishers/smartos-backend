package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.TokenHashes;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private AppUserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, organizations, refreshTokens, passwordEncoder, jwtService);
  }

  @Test
  void loginIssuesTokensForSingleEmailMatch() {
    Organization org = org();
    AppUser user = user(org, "admin@acme.test");
    when(users.findByEmailIgnoreCase("admin@acme.test")).thenReturn(List.of(user));
    when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
    when(jwtService.generateAccessToken(eq(user.getId()), eq(org.getId()), eq(List.of("ORG_ADMIN")),
        eq("admin@acme.test")))
        .thenReturn("access");
    when(jwtService.generateRefreshToken(eq(user.getId()), eq(org.getId()))).thenReturn("refresh");

    TokenResponse tokens = service.login(new LoginRequest("admin@acme.test", "secret123", null));

    assertEquals("access", tokens.getAccessToken());
    assertEquals("refresh", tokens.getRefreshToken());
  }

  @Test
  void loginWithEmailInMultipleOrgsRequiresOrganizationId() {
    Organization orgA = org();
    Organization orgB = org();
    when(users.findByEmailIgnoreCase("shared@test.example"))
        .thenReturn(List.of(user(orgA, "shared@test.example"), user(orgB, "shared@test.example")));

    ApiException ex = assertThrows(ApiException.class,
        () -> service.login(new LoginRequest("shared@test.example", "secret123", null)));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    assertEquals("AMBIGUOUS_TENANT", ex.getCode());
  }

  @Test
  void loginWithWrongPasswordIsRejected() {
    when(users.findByEmailIgnoreCase("admin@acme.test"))
        .thenReturn(List.of(user(org(), "admin@acme.test")));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    ApiException ex = assertThrows(ApiException.class,
        () -> service.login(new LoginRequest("admin@acme.test", "wrong", null)));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
  }

  @Test
  void loginToleratesDuplicateRefreshStore() {
    Organization org = org();
    AppUser user = user(org, "admin@acme.test");
    when(users.findByEmailIgnoreCase("admin@acme.test")).thenReturn(List.of(user));
    when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
    when(jwtService.generateAccessToken(eq(user.getId()), eq(org.getId()), eq(List.of("ORG_ADMIN")),
        eq("admin@acme.test")))
        .thenReturn("access");
    when(jwtService.generateRefreshToken(eq(user.getId()), eq(org.getId()))).thenReturn("refresh");
    RefreshToken existing = new RefreshToken();
    existing.setId(UUID.randomUUID());
    when(refreshTokens.save(any(RefreshToken.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"))
        .thenAnswer(inv -> inv.getArgument(0));
    when(refreshTokens.findByTokenHash(any(String.class))).thenReturn(Optional.of(existing));

    TokenResponse tokens = service.login(new LoginRequest("admin@acme.test", "secret123", null));

    assertEquals("access", tokens.getAccessToken());
    assertEquals("refresh", tokens.getRefreshToken());
  }

  @Test
  void refreshRotatesAndRevokesOldToken() {
    Organization org = org();
    AppUser user = user(org, "admin@acme.test");
    RefreshToken stored = storedToken(user, "old-refresh");
    Claims claims = Mockito.mock(Claims.class);
    when(claims.get("type", String.class)).thenReturn("refresh");
    when(jwtService.parse("old-refresh")).thenReturn(claims);
    when(refreshTokens.findByTokenHash(TokenHashes.sha256Hex("old-refresh")))
        .thenReturn(Optional.of(stored));
    when(jwtService.extractOrgId(claims)).thenReturn(org.getId());
    when(jwtService.generateAccessToken(eq(user.getId()), eq(org.getId()),
        eq(List.of("ORG_ADMIN")), eq("admin@acme.test"))).thenReturn("access-2");
    when(jwtService.generateRefreshToken(eq(user.getId()), eq(org.getId())))
        .thenReturn("refresh-2");
    when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(inv -> {
      RefreshToken r = inv.getArgument(0);
      if (r.getId() == null) {
        r.setId(UUID.randomUUID());
      }
      return r;
    });

    TokenResponse tokens = service.refresh("old-refresh");

    assertEquals("access-2", tokens.getAccessToken());
    assertEquals("refresh-2", tokens.getRefreshToken());
    assertEquals(true, stored.isRevoked());
  }

  @Test
  void refreshReuseRevokesFamily() {
    Organization org = org();
    AppUser user = user(org, "admin@acme.test");
    RefreshToken old = storedToken(user, "old-refresh");
    old.setRevoked(true);
    RefreshToken current = storedToken(user, "current-refresh");
    Claims claims = Mockito.mock(Claims.class);
    when(claims.get("type", String.class)).thenReturn("refresh");
    when(jwtService.parse("old-refresh")).thenReturn(claims);
    when(refreshTokens.findByTokenHash(TokenHashes.sha256Hex("old-refresh")))
        .thenReturn(Optional.of(old));
    when(refreshTokens.findByUserId(eq(user.getId()))).thenReturn(List.of(old, current));

    ApiException ex = assertThrows(ApiException.class, () -> service.refresh("old-refresh"));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    assertEquals(true, current.isRevoked());
  }

  @Test
  void logoutRevokesToken() {
    Organization org = org();
    RefreshToken stored = storedToken(user(org, "a@test.example"), "tok");
    when(refreshTokens.findByTokenHash(TokenHashes.sha256Hex("tok")))
        .thenReturn(Optional.of(stored));

    service.logout("tok");

    assertEquals(true, stored.isRevoked());
  }

  private RefreshToken storedToken(AppUser user, String raw) {
    RefreshToken record = new RefreshToken();
    record.setId(UUID.randomUUID());
    record.setUser(user);
    record.setTokenHash(TokenHashes.sha256Hex(raw));
    record.setExpiresAt(Instant.now().plusSeconds(3600));
    record.setRevoked(false);
    return record;
  }

  private Organization org() {
    Organization org = new Organization();
    org.setId(UUID.randomUUID());
    org.setName("Acme");
    org.setSlug("acme-" + UUID.randomUUID().toString().substring(0, 8));
    return org;
  }

  private AppUser user(Organization org, String email) {
    AppUser user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setOrganization(org);
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setFullName("Admin User");
    user.setRole(Role.ORG_ADMIN);
    user.setActive(true);
    return user;
  }
}
