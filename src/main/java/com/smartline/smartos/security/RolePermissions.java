package com.smartline.smartos.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for what each role may do.
 *
 * <p>Both the API layer and the web app derive their decisions from this
 * matrix: the backend exposes each role's permissions as Spring Security
 * authorities ({@code PERM_*}) and the frontend reads the same matrix from
 * {@code GET /api/v1/auth/permissions}. Changing a role's responsibilities is
 * therefore a one-line change here rather than an edit to every controller and
 * every navigation entry.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Administrators are additive: {@code ORG_ADMIN} and {@code PLATFORM_ADMIN}
 *       hold every permission in the organization, so a new module never leaves
 *       admins locked out.</li>
 *   <li>Roles are scoped to their responsibility. An editor edits manuscripts
 *       and books but never sees customers, orders, or money; a finance user
 *       sees money but not the editorial pipeline.</li>
 *   <li>{@code PLATFORM_ADMIN} is the only role that may change an
 *       organization's plan, and it is the only role that may grant itself or
 *       others platform-wide authority.</li>
 * </ul>
 */
public final class RolePermissions {

  private RolePermissions() {
  }

  /**
   * Editorial content: books, manuscripts, contributors, tasks.
   *
   * <p>{@code EDITOR} may delete titles and contributors; {@code AUTHOR} may
   * only add and update them, matching the previous per-endpoint role lists.
   */
  private static final Set<Permission> EDITORIAL = EnumSet.of(
      Permission.BOOKS_READ,
      Permission.BOOKS_WRITE,
      Permission.BOOKS_DELETE,
      Permission.MANUSCRIPTS_READ,
      Permission.MANUSCRIPTS_WRITE,
      Permission.MANUSCRIPTS_DELETE,
      Permission.CONTRIBUTORS_READ,
      Permission.CONTRIBUTORS_WRITE,
      Permission.CONTRIBUTORS_DELETE,
      Permission.TASKS_READ,
      Permission.TASKS_WRITE,
      Permission.REPORTS_EDITORIAL_READ);

  /** Author scope: contribute content, but never delete other people's work. */
  private static final Set<Permission> AUTHOR = EnumSet.of(
      Permission.BOOKS_READ,
      Permission.BOOKS_WRITE,
      Permission.MANUSCRIPTS_READ,
      Permission.MANUSCRIPTS_WRITE,
      Permission.CONTRIBUTORS_READ,
      Permission.CONTRIBUTORS_WRITE,
      Permission.TASKS_READ);

  /** Production floor: print jobs, stock, warehouses. */
  private static final Set<Permission> PRODUCTION = EnumSet.of(
      Permission.PRODUCTION_READ,
      Permission.PRODUCTION_WRITE,
      Permission.INVENTORY_READ,
      Permission.INVENTORY_WRITE,
      Permission.WAREHOUSES_READ,
      Permission.WAREHOUSES_WRITE);

  /** Order desk: customers and orders. */
  private static final Set<Permission> SALES = EnumSet.of(
      Permission.ORDERS_READ,
      Permission.ORDERS_WRITE,
      Permission.CUSTOMERS_READ,
      Permission.CUSTOMERS_WRITE,
      Permission.INVENTORY_READ,
      Permission.WAREHOUSES_READ);

  /** Money: ledger, budgets, royalties, sales reporting. */
  private static final Set<Permission> FINANCE = EnumSet.of(
      Permission.FINANCE_READ,
      Permission.FINANCE_WRITE,
      Permission.ROYALTIES_READ,
      Permission.ROYALTIES_WRITE,
      Permission.REPORTS_SALES_READ,
      Permission.ORDERS_READ);

  /** Read-only across the publishing pipeline. */
  private static final Set<Permission> READ_ONLY = EnumSet.of(
      Permission.BOOKS_READ,
      Permission.MANUSCRIPTS_READ,
      Permission.CONTRIBUTORS_READ,
      Permission.TASKS_READ,
      Permission.PRODUCTION_READ,
      Permission.INVENTORY_READ);

  /** Everything an organization administrator may do inside their own org. */
  private static final Set<Permission> ORG_ADMIN = EnumSet.complementOf(EnumSet.of(
      Permission.ORG_PLAN_WRITE));

  private static final Map<Role, Set<Permission>> MATRIX = Map.of(
      Role.PLATFORM_ADMIN, EnumSet.allOf(Permission.class),
      Role.ORG_ADMIN, ORG_ADMIN,
      Role.EDITOR, EDITORIAL,
      Role.DESIGNER, EnumSet.of(
          Permission.BOOKS_READ,
          Permission.MANUSCRIPTS_READ,
          Permission.CONTRIBUTORS_READ,
          Permission.TASKS_READ,
          Permission.PRODUCTION_READ),
      Role.PRODUCTION, PRODUCTION,
      Role.WAREHOUSE, EnumSet.of(
          Permission.PRODUCTION_READ,
          Permission.INVENTORY_READ,
          Permission.INVENTORY_WRITE,
          Permission.WAREHOUSES_READ,
          Permission.WAREHOUSES_WRITE,
          Permission.TASKS_READ),
      Role.SALES, SALES,
      Role.FINANCE, FINANCE,
      Role.AUTHOR, AUTHOR,
      Role.VIEWER, READ_ONLY);

  /** Permissions granted to a role. Never null; empty for an unknown role. */
  public static Set<Permission> forRole(Role role) {
    if (role == null) {
      return Set.of();
    }
    return MATRIX.getOrDefault(role, Set.of());
  }

  /** Permissions granted to a set of role names (as they appear in a JWT). */
  public static Set<Permission> forRoleNames(List<String> roleNames) {
    if (roleNames == null || roleNames.isEmpty()) {
      return Set.of();
    }
    Set<Permission> out = EnumSet.noneOf(Permission.class);
    for (String name : roleNames) {
      try {
        out.addAll(forRole(Role.valueOf(name)));
      } catch (IllegalArgumentException ignored) {
        // Unknown role name in a token: contribute no permissions.
      }
    }
    return out;
  }

  public static boolean has(Role role, Permission permission) {
    return forRole(role).contains(permission);
  }

  /**
   * Roles an organization administrator may assign. Platform admin is excluded:
   * granting it is a platform-level action, and
   * {@link #isAssignableByOrgAdmin} enforces that at the service layer.
   */
  public static List<Role> assignableByOrgAdmin() {
    return List.of(
        Role.ORG_ADMIN,
        Role.EDITOR,
        Role.DESIGNER,
        Role.PRODUCTION,
        Role.WAREHOUSE,
        Role.SALES,
        Role.FINANCE,
        Role.AUTHOR,
        Role.VIEWER);
  }

  public static boolean isAssignableByOrgAdmin(Role role) {
    return role != null && assignableByOrgAdmin().contains(role);
  }
}
