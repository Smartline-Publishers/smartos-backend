package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The role matrix is the single source of truth for access control, so these
 * tests pin the behaviour that the API and the web app both rely on.
 */
class RolePermissionsTest {

  @Test
  void editorCannotSeeCommerceOrFinance() {
    Set<Permission> perms = RolePermissions.forRole(Role.EDITOR);

    assertFalse(perms.contains(Permission.ORDERS_READ), "editors must not read orders");
    assertFalse(perms.contains(Permission.ORDERS_WRITE));
    assertFalse(perms.contains(Permission.CUSTOMERS_READ), "editors must not read customers");
    assertFalse(perms.contains(Permission.CUSTOMERS_WRITE));
    assertFalse(perms.contains(Permission.FINANCE_READ), "editors must not read the ledger");
    assertFalse(perms.contains(Permission.FINANCE_WRITE));
    assertFalse(perms.contains(Permission.ROYALTIES_READ), "editors must not read royalties");
    assertFalse(perms.contains(Permission.ROYALTIES_WRITE));
    assertFalse(perms.contains(Permission.REPORTS_SALES_READ), "editors must not read sales reports");
    assertFalse(perms.contains(Permission.USERS_READ), "editors must not list users");
    assertFalse(perms.contains(Permission.USERS_WRITE));
    assertFalse(perms.contains(Permission.AUDIT_READ));
  }

  @Test
  void editorCanDoEditorialWork() {
    Set<Permission> perms = RolePermissions.forRole(Role.EDITOR);

    assertTrue(perms.contains(Permission.BOOKS_READ));
    assertTrue(perms.contains(Permission.BOOKS_WRITE));
    assertTrue(perms.contains(Permission.MANUSCRIPTS_READ));
    assertTrue(perms.contains(Permission.MANUSCRIPTS_WRITE));
    assertTrue(perms.contains(Permission.TASKS_READ));
    assertTrue(perms.contains(Permission.TASKS_WRITE));
    assertTrue(perms.contains(Permission.CONTRIBUTORS_READ));
    assertTrue(perms.contains(Permission.REPORTS_EDITORIAL_READ));
  }

  @Test
  void editorCanSeeTasksButNotCustomers() {
    // The reported symptom: editors saw commerce data on their dashboard.
    Set<Permission> perms = RolePermissions.forRole(Role.EDITOR);
    assertTrue(perms.contains(Permission.TASKS_READ));
    assertFalse(perms.contains(Permission.CUSTOMERS_READ));
    assertFalse(perms.contains(Permission.ORDERS_READ));
  }

  @Test
  void authorMayContributeButNotDelete() {
    Set<Permission> perms = RolePermissions.forRole(Role.AUTHOR);
    assertTrue(perms.contains(Permission.BOOKS_WRITE));
    assertTrue(perms.contains(Permission.MANUSCRIPTS_WRITE));
    assertFalse(perms.contains(Permission.BOOKS_DELETE));
    assertFalse(perms.contains(Permission.MANUSCRIPTS_DELETE));
    assertFalse(perms.contains(Permission.CONTRIBUTORS_DELETE));
  }

  @Test
  void financeRoleSeesMoneyButNotEditorial() {
    Set<Permission> perms = RolePermissions.forRole(Role.FINANCE);
    assertTrue(perms.contains(Permission.FINANCE_READ));
    assertTrue(perms.contains(Permission.FINANCE_WRITE));
    assertTrue(perms.contains(Permission.ROYALTIES_READ));
    assertTrue(perms.contains(Permission.ROYALTIES_WRITE));
    assertTrue(perms.contains(Permission.REPORTS_SALES_READ));
    assertFalse(perms.contains(Permission.MANUSCRIPTS_WRITE));
    assertFalse(perms.contains(Permission.TASKS_WRITE));
    assertFalse(perms.contains(Permission.USERS_READ));
  }

  @Test
  void salesRoleSeesOrdersAndCustomersButNotTheLedger() {
    Set<Permission> perms = RolePermissions.forRole(Role.SALES);
    assertTrue(perms.contains(Permission.ORDERS_READ));
    assertTrue(perms.contains(Permission.ORDERS_WRITE));
    assertTrue(perms.contains(Permission.CUSTOMERS_READ));
    assertTrue(perms.contains(Permission.CUSTOMERS_WRITE));
    assertFalse(perms.contains(Permission.FINANCE_READ));
    assertFalse(perms.contains(Permission.ROYALTIES_READ));
  }

  @Test
  void orgAdminManagesUsersAndOrganizationButNotPlans() {
    Set<Permission> perms = RolePermissions.forRole(Role.ORG_ADMIN);
    assertTrue(perms.contains(Permission.USERS_READ));
    assertTrue(perms.contains(Permission.USERS_WRITE));
    assertTrue(perms.contains(Permission.INVITES_MANAGE));
    assertTrue(perms.contains(Permission.AUDIT_READ));
    assertTrue(perms.contains(Permission.ORG_SETTINGS_WRITE));
    // Changing an organization's plan is a platform-level action.
    assertFalse(perms.contains(Permission.ORG_PLAN_WRITE));
  }

  @Test
  void platformAdminHoldsEveryPermission() {
    Set<Permission> perms = RolePermissions.forRole(Role.PLATFORM_ADMIN);
    assertEquals(EnumSet.allOf(Permission.class), perms);
    assertEquals(Permission.values().length, perms.size());
  }

  @Test
  void viewerIsReadOnly() {
    Set<Permission> perms = RolePermissions.forRole(Role.VIEWER);
    assertTrue(perms.contains(Permission.BOOKS_READ));
    assertFalse(perms.contains(Permission.BOOKS_WRITE));
    assertFalse(perms.contains(Permission.ORDERS_READ));
    assertFalse(perms.contains(Permission.FINANCE_READ));
    assertFalse(perms.contains(Permission.USERS_READ));
  }

  @Test
  void forRoleNamesResolvesTheSameAsForRole() {
    assertEquals(RolePermissions.forRole(Role.EDITOR),
        RolePermissions.forRoleNames(List.of("EDITOR")));
  }

  @Test
  void unknownRoleNameGrantsNoPermissions() {
    assertEquals(Set.of(), RolePermissions.forRoleNames(List.of("NOT_A_ROLE")));
    assertEquals(Set.of(), RolePermissions.forRoleNames(null));
    assertEquals(Set.of(), RolePermissions.forRoleNames(List.of()));
  }

  @Test
  void orgAdminCannotGrantPlatformAdmin() {
    assertFalse(RolePermissions.isAssignableByOrgAdmin(Role.PLATFORM_ADMIN));
    assertTrue(RolePermissions.isAssignableByOrgAdmin(Role.EDITOR));
    assertTrue(RolePermissions.isAssignableByOrgAdmin(Role.ORG_ADMIN));
    assertTrue(RolePermissions.isAssignableByOrgAdmin(Role.VIEWER));
    assertFalse(RolePermissions.isAssignableByOrgAdmin(null));
  }
}
