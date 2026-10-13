package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import java.util.List;
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
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public Organization get(java.util.UUID id) {
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Organization", id));
  }
}
