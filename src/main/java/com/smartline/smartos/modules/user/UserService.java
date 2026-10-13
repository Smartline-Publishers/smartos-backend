package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final PasswordEncoder passwordEncoder;

  public UserService(AppUserRepository users, OrganizationRepository organizations,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.organizations = organizations;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  @Audited(action = "user.create", entityType = "AppUser")
  public AppUser create(CreateUserRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    String email = req.getEmail().trim().toLowerCase();
    if (users.existsByOrganizationIdAndEmail(orgId, email)) {
      throw ApiException.conflict("User email already exists in organization: " + email);
    }
    AppUser user = new AppUser();
    user.setOrganization(org);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
    user.setFullName(req.getFullName().trim());
    user.setRole(req.getRole() != null ? req.getRole() : Role.VIEWER);
    user.setActive(true);
    return users.save(user);
  }

  @Transactional(readOnly = true)
  public Page<AppUser> list(Pageable pageable) {
    UUID orgId = TenantContext.require();
    return users.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public AppUser get(UUID id) {
    UUID orgId = TenantContext.require();
    return users.findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> ApiException.notFound("User", id));
  }

  @Transactional
  @Audited(action = "user.update", entityType = "AppUser")
  public AppUser update(UUID id, UpdateUserRequest req) {
    AppUser user = get(id);
    if (req.getFullName() != null && !req.getFullName().isBlank()) {
      user.setFullName(req.getFullName().trim());
    }
    if (req.getRole() != null) {
      user.setRole(req.getRole());
    }
    if (req.getActive() != null) {
      user.setActive(req.getActive());
    }
    return users.save(user);
  }

  @Transactional
  @Audited(action = "user.delete", entityType = "AppUser")
  public void delete(UUID id) {
    AppUser user = get(id);
    users.delete(user);
  }
}
