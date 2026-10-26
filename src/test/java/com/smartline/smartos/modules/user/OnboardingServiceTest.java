package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.user.OnboardingDtos.ChecklistResponse;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

  @Mock private AppUserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private InviteRepository invites;
  @Mock private OnboardingProgressRepository progress;

  private OnboardingService service;
  private UUID orgId;
  private UUID userId;
  private Organization org;
  private AppUser user;

  @BeforeEach
  void setUp() {
    service = new OnboardingService(users, organizations, invites, progress);
    orgId = UUID.randomUUID();
    userId = UUID.randomUUID();
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    user = new AppUser();
    user.setId(userId);
    user.setOrganization(org);
    user.setEmail("admin@acme.test");
    user.setFullName("Admin User");
    user.setRole(Role.ORG_ADMIN);
    user.setActive(true);
    UserPrincipal principal = new UserPrincipal(userId, orgId, List.of(Role.ORG_ADMIN.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(progress.findById(userId)).thenReturn(Optional.empty());
    // Only consulted for the admin-only "invite a teammate" step.
    lenient().when(users.countByOrganization_Id(orgId)).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void freshAdminHasIncompleteNonOptionalSteps() {
    when(invites.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());
    ChecklistResponse res = service.checklist();

    assertEquals(5, res.getItems().size());
    assertFalse(res.isComplete());
    assertFalse(res.isDismissed());
    assertTrue(res.getItems().stream().filter(i -> !i.isOptional())
        .noneMatch(OnboardingDtos.ChecklistItem::isCompleted));
    assertTrue(res.getItems().stream()
        .filter(i -> i.getKey().equals("theme") || i.getKey().equals("team"))
        .allMatch(OnboardingDtos.ChecklistItem::isOptional));
  }

  @Test
  void completedSetupReportsComplete() {
    org.setLogoUrl("https://cdn.acme.test/logo.png");
    org.setTheme(Map.of("light", Map.of(), "dark", Map.of()));
    user.setAvatarUrl("https://cdn.acme.test/me.png");
    user.setTotpEnabled(true);
    when(users.countByOrganization_Id(orgId)).thenReturn(2L);

    ChecklistResponse res = service.checklist();

    assertTrue(res.isComplete());
    assertTrue(res.getItems().stream().allMatch(OnboardingDtos.ChecklistItem::isCompleted));
  }

  @Test
  void skippedThemeCountsAsDoneButStaysOptional() {
    OnboardingProgress saved = new OnboardingProgress();
    saved.setUserId(userId);
    saved.setDismissed(false);
    saved.setThemeSkipped(true);
    when(progress.findById(userId)).thenReturn(Optional.of(saved));

    ChecklistResponse res = service.checklist();

    assertTrue(res.getItems().stream().filter(i -> i.getKey().equals("theme"))
        .allMatch(i -> i.isCompleted() && i.isOptional()));
  }

  @Test
  void updatePersistsDismissal() {
    OnboardingProgress saved = new OnboardingProgress();
    saved.setUserId(userId);
    when(progress.findById(userId)).thenReturn(Optional.of(saved));
    when(progress.save(any(OnboardingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

    OnboardingDtos.UpdateOnboardingRequest req = new OnboardingDtos.UpdateOnboardingRequest();
    req.setDismissed(true);
    ChecklistResponse res = service.update(req);

    assertTrue(res.isDismissed());
    assertTrue(saved.isDismissed());
  }

  @Test
  void pendingInviteCountsAsTeamStarted() {
    Invite invite = new Invite();
    invite.setId(UUID.randomUUID());
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    when(invites.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of(invite));

    ChecklistResponse res = service.checklist();

    assertTrue(res.getItems().stream().filter(i -> i.getKey().equals("team"))
        .allMatch(OnboardingDtos.ChecklistItem::isCompleted));
  }

  @Test
  void regularMemberSeesOnlyPersonalTasks() {
    // A non-admin must not be shown organization branding, security
    // configuration or teammate invitations they cannot perform.
    user.setRole(Role.EDITOR);
    authenticateAs(Role.EDITOR);

    ChecklistResponse res = service.checklist();

    assertEquals(2, res.getItems().size());
    assertTrue(res.getItems().stream().map(OnboardingDtos.ChecklistItem::getKey)
        .allMatch(k -> k.equals("profile") || k.equals("two-factor")));
    assertTrue(res.getItems().stream().noneMatch(i -> i.getKey().equals("logo")));
    assertTrue(res.getItems().stream().noneMatch(i -> i.getKey().equals("theme")));
    assertTrue(res.getItems().stream().noneMatch(i -> i.getKey().equals("team")));
  }

  @Test
  void memberTwoFactorIsOptionalButAdminTwoFactorIsRequired() {
    user.setRole(Role.EDITOR);
    authenticateAs(Role.EDITOR);

    ChecklistResponse member = service.checklist();
    assertTrue(member.getItems().stream().filter(i -> i.getKey().equals("two-factor"))
        .allMatch(OnboardingDtos.ChecklistItem::isOptional));

    user.setRole(Role.ORG_ADMIN);
    authenticateAs(Role.ORG_ADMIN);
    when(invites.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());

    ChecklistResponse admin = service.checklist();
    assertTrue(admin.getItems().stream().filter(i -> i.getKey().equals("two-factor"))
        .noneMatch(OnboardingDtos.ChecklistItem::isOptional));
  }

  @Test
  void progressCountsOnlyRequiredSteps() {
    // Optional tasks must not affect the progress numerator or denominator.
    org.setLogoUrl("https://cdn.acme.test/logo.png");
    user.setAvatarUrl("https://cdn.acme.test/me.png");
    user.setTotpEnabled(true);
    when(users.countByOrganization_Id(orgId)).thenReturn(2L);

    ChecklistResponse res = service.checklist();

    long required = res.getItems().stream().filter(i -> !i.isOptional()).count();
    long optional = res.getItems().stream().filter(OnboardingDtos.ChecklistItem::isOptional).count();
    assertEquals(required, res.getRequiredTotal());
    assertEquals(optional, res.getOptionalTotal());
    assertEquals(res.getRequiredTotal(), res.getRequiredCompleted());
    assertTrue(res.isComplete());
  }

  @Test
  void checklistReflectsLiveRoleNotStaleToken() {
    // The stored role is the source of truth: a user promoted to admin must
    // see the admin tasks immediately, without needing a new token.
    user.setRole(Role.ORG_ADMIN);
    when(invites.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());

    ChecklistResponse res = service.checklist();

    assertTrue(res.getItems().stream().anyMatch(i -> i.getKey().equals("logo")));
  }
  private void authenticateAs(Role role) {
    UserPrincipal principal = new UserPrincipal(userId, orgId, List.of(role.name()));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }
}
