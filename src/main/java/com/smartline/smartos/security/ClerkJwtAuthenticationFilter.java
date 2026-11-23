package com.smartline.smartos.security;

import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a Clerk-issued session token.
 *
 * Active only when {@code app.clerk.jwks-url} is configured; otherwise it is a
 * no-op so existing deployments keep working on the legacy JWT flow.
 */
@Component
public class ClerkJwtAuthenticationFilter extends OncePerRequestFilter {

  private final AppUserRepository users;
  private final com.smartline.smartos.modules.organization.OrganizationService organizations;
  private final String jwksUrl;
  private final String secretKey;
  private volatile JwtDecoder decoder;

  public ClerkJwtAuthenticationFilter(
      AppUserRepository users,
      com.smartline.smartos.modules.organization.OrganizationService organizations,
      @Value("${app.clerk.jwks-url:}") String jwksUrl,
      @Value("${app.clerk.secret-key:}") String secretKey) {
    this.users = users;
    this.organizations = organizations;
    this.jwksUrl = jwksUrl == null ? "" : jwksUrl.trim();
    this.secretKey = secretKey == null ? "" : secretKey.trim();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (jwksUrl.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        Jwt jwt = decoder().decode(token);
        String clerkUserId = jwt.getSubject();
        if (clerkUserId != null) {
          AppUser user = users.findByClerkUserId(clerkUserId).orElse(null);
          // First-sign-in linking: if no user is linked yet, resolve by email
          // and stamp clerk_user_id for future requests.
          if (user == null) {
            String email = jwt.getClaimAsString("email");
            if (email == null && !secretKey.isEmpty()) {
              email = fetchClerkEmail(clerkUserId);
            }
            if (email != null) {
              java.util.List<AppUser> matches = users.findByEmailIgnoreCase(email);
              if (matches.size() == 1) {
                user = matches.get(0);
                user.setClerkUserId(clerkUserId);
                users.save(user);
              } else if (matches.isEmpty()) {
                // Auto-provision a new AppUser for this Clerk account. The
                // organization is attached later when the user creates one.
                AppUser created = new AppUser();
                created.setClerkUserId(clerkUserId);
                created.setEmail(email);
                created.setFullName(email.split("@")[0]);
                created.setRole(com.smartline.smartos.security.Role.VIEWER);
                created.setActive(true);
                created.setPasswordHash("clerk-managed");
                user = users.save(created);
              }
            }
          }
          // Sync Clerk-managed profile picture into the backend so onboarding
          // and the app UI reflect what the user already did in Clerk.
          if (user != null && !secretKey.isEmpty()) {
            String imageUrl = fetchClerkImageUrl(clerkUserId);
            if (imageUrl != null && !imageUrl.isBlank() && !imageUrl.equals(user.getAvatarUrl())) {
              user.setAvatarUrl(imageUrl);
              users.save(user);
            }
          }
          if (user != null && user.isActive()) {
            // Sync Clerk organization state into the backend when an org is active.
            String clerkOrgId = jwt.getClaimAsString("org_id");
            if (clerkOrgId != null && !clerkOrgId.isBlank()) {
              String orgName = jwt.getClaimAsString("org_name");
              if (orgName == null && !secretKey.isEmpty()) {
                orgName = fetchClerkOrgName(clerkOrgId);
              }
              com.smartline.smartos.modules.organization.Organization org =
                  organizations.ensureForClerkOrg(clerkOrgId, orgName);
              // Only attach if the user has no org yet, or already belongs to this one.
              if (user.getOrganization() == null
                  || clerkOrgId.equals(user.getOrganization().getClerkOrgId())) {
                user.setOrganization(org);
                String orgRole = jwt.getClaimAsString("org_role");
                if (orgRole != null && orgRole.toLowerCase().contains("admin")) {
                  user.setRole(com.smartline.smartos.security.Role.ORG_ADMIN);
                }
                users.save(user);
              }
            }
            List<String> roles = List.of(user.getRole().name());
            UserPrincipal principal = new UserPrincipal(user.getId(), user.getOrganizationId(), roles);
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (user.getOrganizationId() != null) {
              TenantContext.setCurrentOrg(user.getOrganizationId());
            }
          }
        }
      } catch (Exception ignored) {
        // Not a valid Clerk token; fall through to legacy filter / 401.
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (request.getRequestURI() == null || !request.getRequestURI().startsWith("/api/")) {
        TenantContext.clear();
      }
    }
  }

  /**
   * Fetches the primary email for a Clerk user via the Backend API.
   * Returns null if the secret key is missing or the request fails.
   */
  private String fetchClerkEmail(String clerkUserId) {
    if (secretKey.isEmpty()) return null;
    try {
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create("https://api.clerk.com/v1/users/" + clerkUserId))
          .header("Authorization", "Bearer " + secretKey)
          .GET()
          .build();
      java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) return null;
      // Minimal JSON extraction: look for "email_address":"..." in primary_email_address_id context.
      // Clerk returns email_addresses array; pick the first with verification status.
      String body = res.body();
      int idx = body.indexOf("\"email_address\":\"");
      if (idx < 0) idx = body.indexOf("\"email_address\": \"");
      if (idx < 0) return null;
      int start = body.indexOf("\"", idx + 15) + 1;
      int end = body.indexOf("\"", start);
      if (start <= 0 || end <= start) return null;
      return body.substring(start, end);
    } catch (Exception e) {
      return null;
    }
  }

  private String fetchClerkOrgName(String clerkOrgId) {
    if (secretKey.isEmpty()) return null;
    try {
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create("https://api.clerk.com/v1/organizations/" + clerkOrgId))
          .header("Authorization", "Bearer " + secretKey)
          .GET()
          .build();
      java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) return null;
      String body = res.body();
      int idx = body.indexOf("\"name\":\"");
      if (idx < 0) idx = body.indexOf("\"name\": \"");
      if (idx < 0) return null;
      int start = body.indexOf("\"", idx + 7) + 1;
      int end = body.indexOf("\"", start);
      if (start <= 0 || end <= start) return null;
      return body.substring(start, end);
    } catch (Exception e) {
      return null;
    }
  }

  private String fetchClerkImageUrl(String clerkUserId) {
    if (secretKey.isEmpty()) return null;
    try {
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create("https://api.clerk.com/v1/users/" + clerkUserId))
          .header("Authorization", "Bearer " + secretKey)
          .GET()
          .build();
      java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) return null;
      String body = res.body();
      for (String key : new String[] { "profile_image_url", "image_url" }) {
        String needle = "\"" + key + "\":\"";
        int idx = body.indexOf(needle);
        if (idx < 0) {
          needle = "\"" + key + "\": \"";
          idx = body.indexOf(needle);
        }
        if (idx >= 0) {
          int start = body.indexOf("\"", idx + needle.length() - 1) + 1;
          int end = body.indexOf("\"", start);
          if (start > 0 && end > start) {
            String url = body.substring(start, end);
            if (!url.isBlank() && !url.equals("null")) return url;
          }
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private JwtDecoder decoder() {
    JwtDecoder d = decoder;
    if (d == null) {
      synchronized (this) {
        d = decoder;
        if (d == null) {
          d = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
          decoder = d;
        }
      }
    }
    return d;
  }
}