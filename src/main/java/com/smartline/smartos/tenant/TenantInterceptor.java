package com.smartline.smartos.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.security.JwtService;

@Component
public class TenantInterceptor implements HandlerInterceptor {

  private final JwtService jwtService;

  public TenantInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Prefer JWT claim orgId; fallback to X-Tenant-Id header for dev.
    UUID orgId = null;
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      orgId = principal.getOrgId();
    }
    if (orgId == null) {
      String header = request.getHeader("X-Tenant-Id");
      if (header != null && !header.isBlank()) {
        try {
          orgId = UUID.fromString(header.trim());
        } catch (IllegalArgumentException ignored) {
          // invalid header -> leave null; TenantContext.require() will fail downstream
        }
      }
    }
    if (orgId != null) {
      TenantContext.setCurrentOrg(orgId);
    }
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clear();
  }
}
