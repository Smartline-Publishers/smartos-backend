package com.smartline.smartos.security;

import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AppUserRepository users;

  public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users) {
    this.jwtService = jwtService;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      SecurityContextHolder.clearContext();
      String token = header.substring(7);
      UUID userId = null;
      int tokenVersion = 0;
      try {
        Claims claims = jwtService.parse(token);
        String subject = claims.getSubject();
        if ("access".equals(claims.get("type", String.class)) && subject != null) {
          userId = UUID.fromString(subject);
          tokenVersion = jwtService.extractAuthVersion(claims);
        }
      } catch (JwtException | IllegalArgumentException ignored) {
        // Invalid/expired credentials are treated as anonymous; the security
        // entry point then responds with 401 for protected endpoints.
      }

      AppUser user = userId != null ? users.findById(userId).orElse(null) : null;
      // A password change/reset bumps auth_version. Tokens minted before that
      // carry an older version and are treated as anonymous immediately, even
      // if their cryptographic expiry has not passed yet.
      if (user != null && user.getAuthVersion() != tokenVersion) {
        user = null;
      }
      if (user != null && user.isActive()) {
        // The token proves *who* the caller is; the database is the authority on
        // *what* they may do right now. Resolving the live role here means a role
        // change or a deactivation takes effect on the next request instead of
        // waiting for the access token to expire.
        UUID orgId = user.getOrganizationId();
        List<String> roles = List.of(user.getRole().name());
        UserPrincipal principal = new UserPrincipal(userId, orgId, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        if (orgId != null) {
          TenantContext.setCurrentOrg(orgId);
        }
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      // TenantContext is also cleared by TenantInterceptor; clear here for non-/api paths.
      if (request.getRequestURI() == null || !request.getRequestURI().startsWith("/api/")) {
        TenantContext.clear();
      }
    }
  }
}
