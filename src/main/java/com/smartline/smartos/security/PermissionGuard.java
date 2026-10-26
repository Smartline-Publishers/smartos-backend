package com.smartline.smartos.security;

import com.smartline.smartos.common.exception.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Service-layer permission checks.
 *
 * <p>{@code @PreAuthorize} on controllers protects HTTP entry points, but
 * service methods are also reachable from other services, scheduled work and
 * event handlers. Anything that reads or mutates another user's data calls
 * {@link #require(Permission)} so the check cannot be bypassed by reaching the
 * service directly.
 */
@Component
public class PermissionGuard {

  /** All permissions the current caller holds. */
  public Set<Permission> current() {
    UserPrincipal principal = principal();
    return principal == null ? Set.of() : RolePermissions.forRoleNames(principal.getRoles());
  }

  public boolean can(Permission permission) {
    return current().contains(permission);
  }

  public boolean canAll(Permission... permissions) {
    Set<Permission> held = current();
    for (Permission p : permissions) {
      if (!held.contains(p)) {
        return false;
      }
    }
    return true;
  }

  /** Throws 403 unless the caller holds the permission. */
  public void require(Permission permission) {
    if (!can(permission)) {
      throw ApiException.forbidden("Your role does not allow " + permission.getName());
    }
  }

  public void requireAll(Permission... permissions) {
    for (Permission p : permissions) {
      require(p);
    }
  }

  /** The caller's roles, or an empty list when unauthenticated. */
  public List<String> currentRoles() {
    UserPrincipal principal = principal();
    return principal == null ? List.of() : principal.getRoles();
  }

  public boolean hasRole(Role role) {
    return currentRoles().contains(role.name());
  }

  public boolean isPlatformAdmin() {
    return hasRole(Role.PLATFORM_ADMIN);
  }

  public boolean isOrgAdmin() {
    return hasRole(Role.ORG_ADMIN) || isPlatformAdmin();
  }

  private UserPrincipal principal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
  }
}
