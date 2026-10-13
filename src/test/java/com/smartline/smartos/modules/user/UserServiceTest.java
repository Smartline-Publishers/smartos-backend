package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
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

  private UserService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new UserService(users, organizations, passwordEncoder);
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
    when(users.countByOrganizationId(eq(orgId))).thenReturn(5L);

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
    when(users.countByOrganizationId(eq(orgId))).thenReturn(2L);
    when(users.existsByOrganizationIdAndEmail(eq(orgId), eq("new@acme.test")))
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
    when(users.countByOrganizationId(eq(orgId))).thenReturn(1L);

    CreateUserRequest req = new CreateUserRequest();
    req.setEmail("root@acme.test");
    req.setPassword("secret123");
    req.setFullName("Root User");
    req.setRole(Role.PLATFORM_ADMIN);

    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    verify(users, never()).save(any());
  }

  private void authenticate(Role role) {
    UserPrincipal principal =
        new UserPrincipal(UUID.randomUUID(), orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }
}
