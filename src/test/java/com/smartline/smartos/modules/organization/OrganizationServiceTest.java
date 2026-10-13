package com.smartline.smartos.modules.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
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

/**
 * Tenant-isolation for platform-level resources: ORG_ADMIN callers must never
 * see other organizations; PLATFORM_ADMIN retains full visibility.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

  @Mock private OrganizationRepository repository;

  private OrganizationService service;
  private Organization orgA;
  private Organization orgB;

  @BeforeEach
  void setUp() {
    service = new OrganizationService(repository);
    orgA = org("Acme", "acme");
    orgB = org("Globex", "globex");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void platformAdminListsAllOrganizations() {
    authenticate(Role.PLATFORM_ADMIN, orgA.getId());
    when(repository.findAll()).thenReturn(List.of(orgA, orgB));

    assertEquals(2, service.list().size());
  }

  @Test
  void orgAdminListsOnlyOwnOrganization() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));

    List<Organization> result = service.list();

    assertEquals(List.of(orgA), result);
    verify(repository, never()).findAll();
  }

  @Test
  void orgAdminGetForeignOrganizationReturns404() {
    authenticate(Role.ORG_ADMIN, orgA.getId());

    ApiException ex = assertThrows(ApiException.class, () -> service.get(orgB.getId()));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    verify(repository, never()).findById(any());
  }

  @Test
  void platformAdminGetForeignOrganization() {
    authenticate(Role.PLATFORM_ADMIN, orgA.getId());
    when(repository.findById(orgB.getId())).thenReturn(Optional.of(orgB));

    assertEquals(orgB, service.get(orgB.getId()));
  }

  private Organization org(String name, String slug) {
    Organization org = new Organization();
    org.setId(UUID.randomUUID());
    org.setName(name);
    org.setSlug(slug);
    return org;
  }

  private void authenticate(Role role, UUID orgId) {
    UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }
}
