package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.RegisterOrgRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(AppUserRepository users, OrganizationRepository organizations,
      PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.users = users;
    this.organizations = organizations;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public TokenResponse bootstrapOrganization(RegisterOrgRequest req) {
    String slug = req.getSlug().trim().toLowerCase();
    if (organizations.existsBySlug(slug)) {
      throw ApiException.conflict("Organization slug already exists: " + slug);
    }
    Organization org = new Organization();
    org.setName(req.getOrganizationName().trim());
    org.setSlug(slug);
    org.setPlan(Organization.Plan.FREE);
    Organization saved = organizations.save(org);

    AppUser admin = new AppUser();
    admin.setOrganization(saved);
    admin.setEmail(req.getAdminEmail().trim().toLowerCase());
    admin.setPasswordHash(passwordEncoder.encode(req.getAdminPassword()));
    admin.setFullName(req.getAdminFullName().trim());
    admin.setRole(Role.ORG_ADMIN);
    admin.setActive(true);
    AppUser savedAdmin = users.save(admin);

    List<String> roles = List.of(savedAdmin.getRole().name());
    return new TokenResponse(
        jwtService.generateAccessToken(savedAdmin.getId(), saved.getId(), roles),
        jwtService.generateRefreshToken(savedAdmin.getId(), saved.getId()),
        "Bearer");
  }

  @Transactional(readOnly = true)
  public TokenResponse login(LoginRequest req) {
    String email = req.getEmail().trim().toLowerCase();
    AppUser user;
    if (req.getOrganizationId() != null) {
      user = users.findByOrganizationIdAndEmail(req.getOrganizationId(), email)
          .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));
    } else {
      user = users.findByEmailIgnoreCase(email).stream()
          .filter(AppUser::isActive)
          .findFirst()
          .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));
    }
    if (!user.isActive() || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
    }
    UUID orgId = user.getOrganization().getId();
    List<String> roles = List.of(user.getRole().name());
    return new TokenResponse(
        jwtService.generateAccessToken(user.getId(), orgId, roles),
        jwtService.generateRefreshToken(user.getId(), orgId),
        "Bearer");
  }

  @Transactional(readOnly = true)
  public TokenResponse refresh(String refreshToken) {
    Claims claims;
    try {
      claims = jwtService.parse(refreshToken);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    if (!"refresh".equals(claims.get("type", String.class))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    UUID userId = UUID.fromString(claims.getSubject());
    UUID orgId = jwtService.extractOrgId(claims);
    AppUser user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    List<String> roles = List.of(user.getRole().name());
    UUID effectiveOrg = orgId != null ? orgId : user.getOrganization().getId();
    return new TokenResponse(
        jwtService.generateAccessToken(user.getId(), effectiveOrg, roles),
        jwtService.generateRefreshToken(user.getId(), effectiveOrg),
        "Bearer");
  }
}
