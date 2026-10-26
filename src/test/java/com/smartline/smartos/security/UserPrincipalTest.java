package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * The principal expands a role into both ROLE_* and PERM_* authorities, which
 * is what lets controllers guard endpoints with {@code hasAuthority('PERM_...')}.
 */
class UserPrincipalTest {

  @Test
  void editorPrincipalCarriesPermissionAuthorities() {
    UserPrincipal principal = new UserPrincipal(
        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), List.of(Role.EDITOR.name()));

    assertTrue(principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals("ROLE_EDITOR")));
    assertTrue(principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals("PERM_manuscripts.write")));
    // Editors must not receive commerce authorities.
    assertTrue(principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .noneMatch(a -> a.equals("PERM_orders.read")));
    assertTrue(principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .noneMatch(a -> a.equals("PERM_finance.read")));
  }

  @Test
  void platformAdminPrincipalCarriesEveryPermissionAuthority() {
    UserPrincipal principal = new UserPrincipal(
        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
        List.of(Role.PLATFORM_ADMIN.name()));

    for (Permission p : Permission.values()) {
      assertTrue(principal.getAuthorities().stream()
          .map(GrantedAuthority::getAuthority)
          .anyMatch(a -> a.equals(p.authority())),
          "expected authority " + p.authority());
    }
  }

  @Test
  void principalWithoutRolesHasNoAuthorities() {
    UserPrincipal principal = new UserPrincipal(
        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), List.of());
    assertTrue(principal.getAuthorities().isEmpty());
  }
}
