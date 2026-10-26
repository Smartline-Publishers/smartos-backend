package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.organization.OrgTheme;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistItem;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistResponse;
import com.smartline.smartos.modules.user.OnboardingDtos.UpdateOnboardingRequest;
import com.smartline.smartos.security.Role;
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

  @Transactional
  public ChecklistResponse checklist() {
    UserPrincipal principal = currentPrincipal();
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.notFound("User", principal.getUserId()));
    Organization org = organizations.findById(principal.getOrgId())
        .orElseThrow(() -> ApiException.notFound("Organization", principal.getOrgId()));
    OnboardingProgress saved = ensureProgress(user, org);

    // The persisted role is the source of truth: a role changed by an admin
    // takes effect immediately, not when the user's token next refreshes.
    Role role = user.getRole();
    boolean orgAdmin = role == Role.ORG_ADMIN || role == Role.PLATFORM_ADMIN;

    List<ChecklistItem> items = new ArrayList<>();

    // --- Personal tasks: every role can complete these. ---
    // The profile is required for everyone: it is how colleagues recognise you.
    items.add(new ChecklistItem("profile", "Set up your profile",
        "Add a profile picture so teammates can recognise you.",
        profileComplete(user), false, "/settings?section=profile"));
    // Two-factor is required for administrators, whose accounts carry the
    // organization's administrative reach; for everyone else it is strongly
    // recommended but must not hold up setup.
    items.add(new ChecklistItem("two-factor", "Set up two-factor authentication",
        orgAdmin
            ? "Required for your administrator account — protect it with an authenticator app."
            : "Protect your account with an authenticator app.",
        user.isTotpEnabled(), !orgAdmin, "/settings?section=security"));

    // --- Organization tasks: only for roles that can actually perform them. ---
    if (orgAdmin) {
      items.add(new ChecklistItem("logo", "Add an organization logo",
          "Upload a logo so your press is recognizable across the app.",
          org.getLogoUrl() != null && !org.getLogoUrl().isBlank(), true,
          "/settings?section=organization"));
      boolean themeDone = !OrgTheme.isBlank(org.getTheme()) || saved.isThemeSkipped();
      items.add(new ChecklistItem("theme", "Configure the organization theme",
          "Brand colors for light and dark mode. Optional — the default theme applies until set.",
          themeDone, true, "/settings?section=organization"));

      long memberCount = users.countByOrganization_Id(org.getId());
      // Only offer to invite when the plan still has room: a task that can
      // never be performed and never be completed is noise, not guidance.
      if (memberCount < org.getPlan().getMaxUsers()) {
        items.add(new ChecklistItem("team", "Invite a teammate",
            "Bring at least one more person into the organization.",
            teamStarted(org.getId(), memberCount), true, "/users"));
      }
    }

    int requiredTotal = 0;
    int requiredCompleted = 0;
    int optionalTotal = 0;
    for (ChecklistItem item : items) {
      if (item.isOptional()) {
        optionalTotal++;
      } else {
        requiredTotal++;
        if (item.isCompleted()) {
          requiredCompleted++;
        }
      }
    }
    // Optional tasks never block completion.
    boolean complete = requiredTotal == 0 || requiredCompleted == requiredTotal;
    return new ChecklistResponse(items, complete, saved.isDismissed(),
        requiredCompleted, requiredTotal, optionalTotal);
  }

  /** A profile is complete once it has both a name and a picture. */
  private boolean profileComplete(AppUser user) {
    return user.getFullName() != null && !user.getFullName().isBlank()
        && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
  }

  @Transactional
  public ChecklistResponse update(UpdateOnboardingRequest req) {
    UserPrincipal principal = currentPrincipal();
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> ApiException.notFound("User", principal.getUserId()));
    Organization org = organizations.findById(principal.getOrgId())
        .orElseThrow(() -> ApiException.notFound("Organization", principal.getOrgId()));
    OnboardingProgress saved = ensureProgress(user, org);
    if (req.getThemeSkipped() != null) {
      saved.setThemeSkipped(req.getThemeSkipped());
    }
    if (req.getDismissed() != null) {
      saved.setDismissed(req.getDismissed());
    }
    progress.save(saved);
    return checklist();
  }

  /**
   * Ensures an {@link OnboardingProgress} row exists for the given user, creating one
   * with defaults when it is missing. Every user gets their own independent checklist;
   * existing users who were created before this existed receive one on first access.
   */
  private OnboardingProgress ensureProgress(AppUser user, Organization org) {
    return progress.findById(user.getId()).orElseGet(() -> {
      OnboardingProgress p = new OnboardingProgress();
      p.setUserId(user.getId());
      p.setUser(user);
      p.setOrganization(org);
      progress.save(p);
      return p;
    });
  }

  /**
   * A team exists once the organization has more than one member, or once an
   * invitation is outstanding.
   */
  private boolean teamStarted(UUID orgId, long memberCount) {
    if (memberCount > 1) {
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
