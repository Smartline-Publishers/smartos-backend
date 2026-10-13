package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private AppUserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, organizations, passwordEncoder, jwtService);
  }

  @Test
  void loginIssuesTokensForSingleEmailMatch() {
    Organization org = org();
    AppUser user = user(org, "admin@acme.test");
    when(users.findByEmailIgnoreCase("admin@acme.test")).thenReturn(List.of(user));
    when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
    when(jwtService.generateAccessToken(eq(user.getId()), eq(org.getId()), eq(List.of("ORG_ADMIN"))))
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
