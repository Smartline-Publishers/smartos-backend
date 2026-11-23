package com.smartline.smartos.modules.organization;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.media.MediaService;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganizationService {

  private final OrganizationRepository repository;
  private final MediaService media;
  private final AppUserRepository users;

  public OrganizationService(OrganizationRepository repository, MediaService media, AppUserRepository users) {
    this.repository = repository;
    this.media = media;
    this.users = users;
  }

  @Transactional
  @Audited(action = "organization.create", entityType = "Organization")
  public Organization create(String name, String slug) {
    String normalizedSlug = slug.trim().toLowerCase();
    if (repository.existsBySlug(normalizedSlug)) {
      throw ApiException.conflict("Organization slug already exists: " + normalizedSlug);
    }
    if (repository.existsByName(name.trim())) {
      throw ApiException.conflict("Organization name already exists: " + name);
    }
    Organization org = new Organization();
    org.setName(name.trim());
    org.setSlug(normalizedSlug);
    org.setPlan(Organization.Plan.FREE);
    return repository.save(org);
  }

  /**
   * Clerk-aware onboarding: creates an organization for the currently authenticated
   * user and makes them its ORG_ADMIN. Idempotent-ish: if the user already belongs
   * to an organization, returns that organization instead of creating a duplicate.
   */
  /**
   * Ensures a backend Organization exists for the given Clerk organization,
   * creating one (and linking it) if needed. Returns the backend organization.
   */
  @Transactional
  public Organization ensureForClerkOrg(String clerkOrgId, String name) {
    if (clerkOrgId == null || clerkOrgId.isBlank()) {
      throw ApiException.badRequest("Clerk organization id is required");
    }
    java.util.Optional<Organization> existing = repository.findByClerkOrgId(clerkOrgId);
    if (existing.isPresent()) {
      return existing.get();
    }
    String baseSlug = (name == null || name.isBlank() ? clerkOrgId : name)
        .trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (baseSlug.isBlank()) baseSlug = "org";
    String slug = baseSlug;
    int i = 1;
    while (repository.existsBySlug(slug)) {
      slug = baseSlug + "-" + i++;
    }
    String orgName = (name == null || name.isBlank()) ? clerkOrgId : name.trim();
    if (repository.existsByName(orgName)) {
      orgName = orgName + " (" + clerkOrgId.substring(0, 6) + ")";
    }
    Organization org = new Organization();
    org.setName(orgName);
    org.setSlug(slug);
    org.setClerkOrgId(clerkOrgId);
    org.setPlan(Organization.Plan.FREE);
    return repository.save(org);
  }

  @Transactional
  public Organization bootstrapForCurrentUser(String name, String slug) {
    UserPrincipal principal = currentPrincipal();
    if (principal == null) {
      throw ApiException.forbidden("Authentication required");
    }
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.forbidden("Authentication required"));
    if (user.getOrganization() != null) {
      return user.getOrganization();
    }
    String normalizedSlug = slug.trim().toLowerCase();
    if (repository.existsBySlug(normalizedSlug)) {
      throw ApiException.conflict("Organization slug already exists: " + normalizedSlug);
    }
    if (repository.existsByName(name.trim())) {
      throw ApiException.conflict("Organization name already exists: " + name);
    }
    Organization org = new Organization();
    org.setName(name.trim());
    org.setSlug(normalizedSlug);
    org.setPlan(Organization.Plan.FREE);
    Organization saved = repository.save(org);

    user.setOrganization(saved);
    user.setRole(Role.ORG_ADMIN);
    user.setActive(true);
    users.save(user);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Organization> list() {
    UserPrincipal principal = currentPrincipal();
    if (isPlatformAdmin(principal)) {
      return repository.findAll();
    }
    if (principal == null || principal.getOrgId() == null) {
      throw ApiException.forbidden("Organization access denied");
    }
    // Org admins see only their own organization: never leak other tenants.
    return repository.findById(principal.getOrgId()).map(List::of).orElseGet(List::of);
  }

  @Transactional(readOnly = true)
  public Organization get(UUID id) {
    UserPrincipal principal = currentPrincipal();
    if (!isPlatformAdmin(principal)
        && (principal == null || !id.equals(principal.getOrgId()))) {
      // 404 (not 403) to avoid revealing whether another tenant's org exists.
      throw ApiException.notFound("Organization", id);
    }
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Organization", id));
  }

  @Transactional(readOnly = true)
  public Organization current() {
    UserPrincipal principal = currentPrincipal();
    if (principal == null || principal.getOrgId() == null) {
      throw ApiException.forbidden("Organization access denied");
    }
    return repository.findById(principal.getOrgId())
        .orElseThrow(() -> ApiException.notFound("Organization", principal.getOrgId()));
  }

  @Transactional
  public Organization changePlan(UUID id, Organization.Plan plan) {
    Organization org = repository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Organization", id));
    org.setPlan(plan);
    return repository.save(org);
  }

  /**
   * Self-service update for the caller's own organization (branding, slug,
   * theme). Tenant isolation comes from the JWT claim, never from client
   * input, so one organization can never touch another's branding.
   */
  @Transactional
  @Audited(action = "organization.update", entityType = "Organization")
  public Organization updateCurrent(OrganizationDtos.UpdateOrganizationRequest req) {
    Organization org = current();
    if (req.getName() != null && !req.getName().isBlank()) {
      String name = req.getName().trim();
      if (!name.equals(org.getName()) && repository.existsByName(name)) {
        throw ApiException.conflict("Organization name already exists: " + name);
      }
      org.setName(name);
    }
    if (req.getSlug() != null && !req.getSlug().isBlank()) {
      String slug = req.getSlug().trim().toLowerCase();
      if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
        throw ApiException.badRequest(
            "Slug may only contain lowercase letters, numbers, and single hyphens");
      }
      if (!slug.equals(org.getSlug()) && repository.existsBySlug(slug)) {
        throw ApiException.conflict("Organization slug already exists: " + slug);
      }
      org.setSlug(slug);
    }
    if (Boolean.TRUE.equals(req.getClearLogo())) {
      org.setLogoUrl(null);
    } else if (req.getLogoUrl() != null) {
      org.setLogoUrl(validatedLogoUrl(req.getLogoUrl()));
    }
    if (req.getTheme() != null) {
      org.setTheme(OrgTheme.validated(req.getTheme()));
    }
    return repository.save(org);
  }

  @Transactional
  @Audited(action = "organization.theme.reset", entityType = "Organization")
  public Organization clearTheme() {
    Organization org = current();
    org.setTheme(null);
    return repository.save(org);
  }

  @Transactional
  @Audited(action = "organization.logo.update", entityType = "Organization")
  public Organization uploadLogo(MultipartFile file) {
    Organization org = current();
    org.setLogoUrl(media.replace(org.getLogoUrl(), file, "smartos/" + org.getId() + "/branding"));
    return repository.save(org);
  }

  static String validatedLogoUrl(String raw) {
    String url = raw.trim();
    if (url.isEmpty()) {
      throw ApiException.badRequest("Logo URL must not be empty");
    }
    if (url.startsWith("data:image/")) {
      // Inline uploads without object storage: cap at ~700KB encoded.
      if (url.length() > 700_000 || !url.contains(";base64,")) {
        throw ApiException.badRequest("Logo image is too large or not base64-encoded");
      }
      return url;
    }
    if ((url.startsWith("https://") || url.startsWith("http://")) && url.length() <= 2048
        && !url.contains(" ")) {
      return url;
    }
    throw ApiException.badRequest("Logo must be an http(s) URL or a data:image/* URL");
  }

  private UserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
  }

  private boolean isPlatformAdmin(UserPrincipal principal) {
    return principal != null && principal.getRoles().contains(Role.PLATFORM_ADMIN.name());
  }
}
