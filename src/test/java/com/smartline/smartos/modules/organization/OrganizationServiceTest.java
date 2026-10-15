package com.smartline.smartos.modules.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import java.util.List;
import java.util.Map;
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
  @Mock private com.smartline.smartos.media.MediaService media;

  private OrganizationService service;
  private Organization orgA;
  private Organization orgB;

  @BeforeEach
  void setUp() {
    service = new OrganizationService(repository, media);
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

  @Test
  void currentReturnsCallerOrganization() {
    authenticate(Role.ORG_ADMIN, orgB.getId());
    when(repository.findById(orgB.getId())).thenReturn(Optional.of(orgB));

    assertEquals(orgB, service.current());
  }

  @Test
  void changePlanUpdatesPlan() {
    orgA.setPlan(Organization.Plan.FREE);
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
    when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

    Organization updated = service.changePlan(orgA.getId(), Organization.Plan.GROWTH);

    assertEquals(Organization.Plan.GROWTH, updated.getPlan());
  }

  @Test
  void updateCurrentChangesNameSlugLogoAndTheme() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
    when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

    OrganizationDtos.UpdateOrganizationRequest req = new OrganizationDtos.UpdateOrganizationRequest();
    req.setName("Acme Press");
    req.setSlug("acme-press");
    req.setLogoUrl("https://cdn.acme.test/logo.png");
    req.setTheme(Map.of("light", mode("#1d4ed8", "#ffffff"), "dark", mode("#93c5fd", "#0a0a0a")));

    Organization updated = service.updateCurrent(req);

    assertEquals("Acme Press", updated.getName());
    assertEquals("acme-press", updated.getSlug());
    assertEquals("https://cdn.acme.test/logo.png", updated.getLogoUrl());
    assertEquals("#1d4ed8", ((Map<?, ?>) updated.getTheme().get("light")).get("primary"));
  }

  @Test
  void updateCurrentRejectsDuplicateSlug() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
    when(repository.existsBySlug("taken")).thenReturn(true);

    OrganizationDtos.UpdateOrganizationRequest req = new OrganizationDtos.UpdateOrganizationRequest();
    req.setSlug("taken");

    ApiException ex = assertThrows(ApiException.class, () -> service.updateCurrent(req));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
  }

  @Test
  void updateCurrentRejectsBadSlugAndLogo() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));

    OrganizationDtos.UpdateOrganizationRequest badSlug = new OrganizationDtos.UpdateOrganizationRequest();
    badSlug.setSlug("Not A Slug!");
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.updateCurrent(badSlug)).getStatus());

    OrganizationDtos.UpdateOrganizationRequest badLogo = new OrganizationDtos.UpdateOrganizationRequest();
    badLogo.setLogoUrl("ftp://acme.test/logo.png");
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.updateCurrent(badLogo)).getStatus());
  }

  @Test
  void updateCurrentRejectsInvalidTheme() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));

    OrganizationDtos.UpdateOrganizationRequest req = new OrganizationDtos.UpdateOrganizationRequest();
    req.setTheme(Map.of("light", mode("#dc2626", "#f5f7fb")));

    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.updateCurrent(req)).getStatus());
  }

  @Test
  void clearThemeResetsToDefault() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    orgA.setTheme(Map.of("light", mode("#1d4ed8", "#ffffff")));
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
    when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(null, service.clearTheme().getTheme());
  }

  @Test
  void uploadLogoStoresHostedUrl() {
    authenticate(Role.ORG_ADMIN, orgA.getId());
    when(repository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
    when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png",
            new byte[] {1, 2, 3});
    when(media.replace(eq(orgA.getLogoUrl()), eq(file), eq("smartos/" + orgA.getId() + "/branding")))
        .thenReturn("https://res.cloudinary.com/demo/image/upload/v1/logo.png");

    Organization updated = service.uploadLogo(file);

    assertEquals("https://res.cloudinary.com/demo/image/upload/v1/logo.png",
        updated.getLogoUrl());
  }

  private static java.util.Map<String, Object> mode(String primary, String shell) {
    return Map.of("primary", primary, "shell", shell, "secondary", "#64748b",
        "tertiary", "#2563eb", "sidebar", "#0a0a0a");
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
