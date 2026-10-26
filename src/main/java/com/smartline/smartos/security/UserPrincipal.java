package com.smartline.smartos.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

  private final UUID userId;
  private final UUID orgId;
  private final List<String> roles;

  public UserPrincipal(UUID userId, UUID orgId, List<String> roles) {
    this.userId = userId;
    this.orgId = orgId;
    this.roles = roles != null ? List.copyOf(roles) : List.of();
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public List<String> getRoles() {
    return roles;
  }

  @Override
  public java.util.Collection<? extends GrantedAuthority> getAuthorities() {
    List<GrantedAuthority> authorities = new java.util.ArrayList<>();
    authorities.addAll(roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
        .toList());
    // Permission authorities are derived from the role matrix, so a single
    // change in RolePermissions propagates to every guarded endpoint.
    authorities.addAll(RolePermissions.forRoleNames(roles).stream()
        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.authority()))
        .toList());
    return authorities;
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return userId != null ? userId.toString() : null;
  }
}
