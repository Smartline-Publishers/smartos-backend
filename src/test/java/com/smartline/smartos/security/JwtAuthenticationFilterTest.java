package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @Mock private AppUserRepository users;
  @Mock private Claims claims;

  @InjectMocks private JwtAuthenticationFilter filter;

  @AfterEach
  void clearSecurityContexts() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void validAccessTokenAuthenticatesWithCurrentRoleAndOrganization() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    AppUser user = activeUser(userId, organizationId);
    when(jwtService.parse("access-token")).thenReturn(claims);
    when(claims.get("type", String.class)).thenReturn("access");
    when(claims.getSubject()).thenReturn(userId.toString());
    when(users.findById(userId)).thenReturn(Optional.of(user));

    filter.doFilter(requestWithToken("access-token"), new MockHttpServletResponse(),
        new MockFilterChain());

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertTrue(authentication.isAuthenticated());
    assertTrue(authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("PERM_users.read")));
    assertEquals(organizationId, ((UserPrincipal) authentication.getPrincipal()).getOrgId());
  }

  @Test
  void refreshTokenCannotAuthenticateProtectedRequests() throws Exception {
    UUID userId = UUID.randomUUID();
    when(jwtService.parse("refresh-token")).thenReturn(claims);
    when(claims.get("type", String.class)).thenReturn("refresh");
    when(claims.getSubject()).thenReturn(userId.toString());

    filter.doFilter(requestWithToken("refresh-token"), new MockHttpServletResponse(),
        new MockFilterChain());

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(users, never()).findById(userId);
  }

  @Test
  void expiredAndMissingTokensRemainUnauthenticated() throws Exception {
    when(jwtService.parse("expired-token")).thenThrow(new JwtException("expired"));

    filter.doFilter(requestWithToken("expired-token"), new MockHttpServletResponse(),
        new MockFilterChain());
    assertNull(SecurityContextHolder.getContext().getAuthentication());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth/me");
    filter.doFilter(request, new MockHttpServletResponse(),
        new MockFilterChain());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void inactiveUserIsNotAuthenticated() throws Exception {
    UUID userId = UUID.randomUUID();
    AppUser user = activeUser(userId, UUID.randomUUID());
    user.setActive(false);
    when(jwtService.parse("access-token")).thenReturn(claims);
    when(claims.get("type", String.class)).thenReturn("access");
    when(claims.getSubject()).thenReturn(userId.toString());
    when(users.findById(userId)).thenReturn(Optional.of(user));

    filter.doFilter(requestWithToken("access-token"), new MockHttpServletResponse(),
        new MockFilterChain());

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  private static MockHttpServletRequest requestWithToken(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth/me");
    request.addHeader("Authorization", "Bearer " + token);
    return request;
  }

  private static AppUser activeUser(UUID userId, UUID organizationId) {
    Organization organization = new Organization();
    organization.setId(organizationId);
    AppUser user = new AppUser();
    user.setId(userId);
    user.setOrganization(organization);
    user.setRole(Role.ORG_ADMIN);
    user.setActive(true);
    return user;
  }
}
