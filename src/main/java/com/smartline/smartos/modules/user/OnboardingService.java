package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.organization.OrgTheme;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistItem;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistResponse;
import com.smartline.smartos.modules.user.OnboardingDtos.UpdateOnboardingRequest;
import com.smartline.smartos.security.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator onboarding checklist. Completion is derived live from actual
 * organization/user state, so progress updates automatically as setup tasks
 * finish on any device; only explicit choices (skipping the optional theme
 * step, dismissing the checklist) are persisted.
 */
@Service
public class OnboardingService {

  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final InviteRepository invites;
  private final OnboardingProgressRepository progress;

  public OnboardingService(AppUserRepository users, OrganizationRepository organizations,
      InviteRepository invites, OnboardingProgressRepository progress) {
    this.users = users;
    this.organizations = organizations;
    this.invites = invites;
    this.progress = progress;
  }

  @Transactional(readOnly = true)
  public ChecklistResponse checklist() {
    UserPrincipal principal = currentPrincipal();
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.notFound("User", principal.getUserId()));
    Organization org = organizations.findById(principal.getOrgId())
        .orElseThrow(() -> ApiException.notFound("Organization", principal.getOrgId()));
    OnboardingProgress saved = progress.findById(user.getId()).orElse(null);
    boolean themeSkipped = saved != null && saved.isThemeSkipped();
    boolean dismissed = saved != null && saved.isDismissed();

    List<ChecklistItem> items = new ArrayList<>();
    items.add(new ChecklistItem("logo", "Add an organization logo",
        "Upload a logo so your press is recognizable across the app.",
        org.getLogoUrl() != null && !org.getLogoUrl().isBlank(), false,
        "/settings?section=organization"));
    items.add(new ChecklistItem("profile", "Set up your profile",
        "Add your name and a profile picture.",
        user.getFullName() != null && !user.getFullName().isBlank()
            && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank(),
        false, "/settings?section=profile"));
    boolean themeDone = !OrgTheme.isBlank(org.getTheme()) || themeSkipped;
    items.add(new ChecklistItem("theme", "Configure the organization theme",
        "Brand colors for light and dark mode. Optional — the default theme applies until set.",
        themeDone, true, "/settings?section=appearance"));
    items.add(new ChecklistItem("two-factor", "Set up two-factor authentication",
        "Protect the administrator account with an authenticator app.",
        user.isTotpEnabled(), false, "/settings?section=security"));
    items.add(new ChecklistItem("team", "Invite a teammate",
        "Bring at least one more person into the organization.",
        teamStarted(org.getId()), true, "/users"));

    boolean complete = items.stream().filter(i -> !i.isOptional()).allMatch(ChecklistItem::isCompleted);
    return new ChecklistResponse(items, complete, dismissed);
  }

  @Transactional
  public ChecklistResponse update(UpdateOnboardingRequest req) {
    UserPrincipal principal = currentPrincipal();
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.notFound("User", principal.getUserId()));
    OnboardingProgress saved = progress.findById(user.getId()).orElseGet(() -> {
      OnboardingProgress p = new OnboardingProgress();
      p.setUserId(user.getId());
      p.setUser(user);
      Organization org = organizations.findById(principal.getOrgId())
          .orElseThrow(() -> ApiException.notFound("Organization", principal.getOrgId()));
      p.setOrganization(org);
      return p;
    });
    if (req.getThemeSkipped() != null) {
      saved.setThemeSkipped(req.getThemeSkipped());
    }
    if (req.getDismissed() != null) {
      saved.setDismissed(req.getDismissed());
    }
    progress.save(saved);
    return checklist();
  }

  private boolean teamStarted(UUID orgId) {
    if (users.countByOrganization_Id(orgId) > 1) {
      return true;
    }
    Instant now = Instant.now();
    return invites.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
        .anyMatch(i -> i.getAcceptedAt() == null && i.getExpiresAt() != null
            && i.getExpiresAt().isAfter(now));
  }

  private UserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal
        && principal.getUserId() != null && principal.getOrgId() != null) {
      return principal;
    }
    throw ApiException.forbidden("Authentication required");
  }
}
