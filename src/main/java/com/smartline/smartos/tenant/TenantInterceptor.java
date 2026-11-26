package com.smartline.smartos.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.smartline.smartos.security.UserPrincipal;

@Component
public class TenantInterceptor implements HandlerInterceptor {
  // NOTE: JWT parsing/validation intentionally lives in JwtAuthenticationFilter,
  // which runs before this interceptor and stores the principal + tenant. This
  // interceptor only re-establishes TenantContext for /api/** requests.

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Tenant identity comes ONLY from the authenticated principal (the JWT/Clerk
    // token). Never trust a client-supplied X-Tenant-Id header: an authenticated
    // user without an organization, or a compromised token, could otherwise read
    // another tenant's data. The header is deliberately ignored.
    UUID orgId = null;
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      orgId = principal.getOrgId();
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
