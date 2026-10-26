package com.smartline.smartos.security;

/**
 * Atomic capabilities a role may hold. Every guarded endpoint and every
 * navigation entry in the web app maps to one of these, so the same matrix
 * ({@link RolePermissions}) drives both API authorization and the UI.
 *
 * <p>Names are dotted by module and suffixed by the action, e.g.
 * {@code books.read}, {@code finance.write}. The Spring Security authority
 * string is {@code PERM_<name>} so {@code @PreAuthorize("hasAuthority('PERM_books.read')")}
 * can be used directly.
 */
public enum Permission {

  // Catalogue
  BOOKS_READ("books.read"),
  BOOKS_WRITE("books.write"),
  BOOKS_DELETE("books.delete"),

  // Editorial
  MANUSCRIPTS_READ("manuscripts.read"),
  MANUSCRIPTS_WRITE("manuscripts.write"),
  MANUSCRIPTS_DELETE("manuscripts.delete"),

  CONTRIBUTORS_READ("contributors.read"),
  CONTRIBUTORS_WRITE("contributors.write"),
  CONTRIBUTORS_DELETE("contributors.delete"),

  TASKS_READ("tasks.read"),
  TASKS_WRITE("tasks.write"),

  // Production & logistics
  PRODUCTION_READ("production.read"),
  PRODUCTION_WRITE("production.write"),

  INVENTORY_READ("inventory.read"),
  INVENTORY_WRITE("inventory.write"),

  WAREHOUSES_READ("warehouses.read"),
  WAREHOUSES_WRITE("warehouses.write"),

  // Commerce
  ORDERS_READ("orders.read"),
  ORDERS_WRITE("orders.write"),

  CUSTOMERS_READ("customers.read"),
  CUSTOMERS_WRITE("customers.write"),

  // Finance
  FINANCE_READ("finance.read"),
  FINANCE_WRITE("finance.write"),

  ROYALTIES_READ("royalties.read"),
  ROYALTIES_WRITE("royalties.write"),

  // Reporting
  REPORTS_SALES_READ("reports.sales.read"),
  REPORTS_EDITORIAL_READ("reports.editorial.read"),

  // Administration
  USERS_READ("users.read"),
  USERS_WRITE("users.write"),
  INVITES_MANAGE("invites.manage"),
  AUDIT_READ("audit.read"),
  ORG_SETTINGS_WRITE("org.settings.write"),
  ORG_PLAN_WRITE("org.plan.write");

  private final String name;

  Permission(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  /** Spring Security authority string for this permission. */
  public String authority() {
    return "PERM_" + name;
  }

  @Override
  public String toString() {
    return name;
  }
}
