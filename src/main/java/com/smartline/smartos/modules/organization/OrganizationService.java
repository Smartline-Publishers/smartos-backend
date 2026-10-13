package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository repository;

  public OrganizationService(OrganizationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  @Audited(action = "organization.create", entityType = "Organization")
  public Organization create(String name, String slug) {
    String normalizedSlug = slug.trim().toLowerCase();
    if (repository.existsBySlug(normalizedSlug)) {
      throw ApiException.conflict("Organization slug already exists: " + normalizedSlug);
    }
    if (repository.existsByName(name.trim())) {
      throw ApiException.conflict("Organization name already exists: " + name);
    }
    Organization org = new Organization();
    org.setName(name.trim());
    org.setSlug(normalizedSlug);
    org.setPlan(Organization.Plan.FREE);
    return repository.save(org);
  }

  @Transactional(readOnly = true)
  public List<Organization> list() {
    UserPrincipal principal = currentPrincipal();
    if (isPlatformAdmin(principal)) {
      return repository.findAll();
    }
    if (principal == null || principal.getOrgId() == null) {
      throw ApiException.forbidden("Organization access denied");
    }
    // Org admins see only their own organization: never leak other tenants.
    return repository.findById(principal.getOrgId()).map(List::of).orElseGet(List::of);
  }

  @Transactional(readOnly = true)
  public Organization get(UUID id) {
    UserPrincipal principal = currentPrincipal();
    if (!isPlatformAdmin(principal)
        && (principal == null || !id.equals(principal.getOrgId()))) {
      // 404 (not 403) to avoid revealing whether another tenant's org exists.
      throw ApiException.notFound("Organization", id);
    }
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Organization", id));
  }

  private UserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
  }

  private boolean isPlatformAdmin(UserPrincipal principal) {
    return principal != null && principal.getRoles().contains(Role.PLATFORM_ADMIN.name());
  }
}
