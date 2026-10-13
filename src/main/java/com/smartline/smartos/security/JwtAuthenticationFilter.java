package com.smartline.smartos.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.smartline.smartos.tenant.TenantContext;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        Claims claims = jwtService.parse(token);
        UUID userId = UUID.fromString(claims.getSubject());
        UUID orgId = jwtService.extractOrgId(claims);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles == null) {
          roles = List.of();
        }
        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .toList();
        UserPrincipal principal = new UserPrincipal(userId, orgId, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        if (orgId != null) {
          TenantContext.setCurrentOrg(orgId);
        }
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
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
