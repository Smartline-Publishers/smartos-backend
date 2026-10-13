# ADR-001: Tenant isolation strategy

Date: 2026-10-13 · Status: **Accepted** (shared-schema enforced) · Next review: 50 tenants or first isolation incident

## Context

SmartOS serves multiple publishing companies from one database and one
deployable. Every business table carries `organization_id`, enforced at three
levels: repository queries filter by tenant, services re-check via
`TenantContext`, and tests assert scoping per module (`TenantIsolationTest`,
`OrganizationServiceTest`, per-service tenant tests). This ADR records why we
stay on shared-schema for now, and what would trigger a move.

## Options considered

### A. Shared schema, discriminator column (current)
- **How:** `organization_id` on every tenant table + FKs; JWT `orgId` claim →
  `TenantContext` → scoped queries; 404 (never 403) on cross-tenant IDs to
  avoid tenant-enumeration oracles.
- **Pros:** one migration chain (Flyway V1–V10), one connection pool,
  cross-tenant platform queries trivial, cheapest to operate, fully covered by
  existing tests.
- **Cons:** isolation is by convention + tests, not by the database; a missing
  `WHERE organization_id` is a leak. Blast radius of a bad migration is global.

### B. PostgreSQL Row-Level Security
- **How:** `ALTER TABLE … ENABLE ROW LEVEL SECURITY` + policies keyed to a
  session variable (`SET app.tenant = '<org>'` per connection/request),
  with `BYPASSRLS` reserved for platform jobs.
- **Pros:** the database enforces isolation even against buggy queries;
  keeps single-schema operations.
- **Cons:** connection-pool `SET` hygiene is easy to get wrong (leaked tenant
  across pooled checkouts = leak); every table needs policies + tests;
  `EXPLAIN`/migrations get harder; Supabase-style JWT claims mapping adds
  moving parts. Meaningful work with no customer-visible payoff today.

### C. Schema-per-tenant (or database-per-tenant)
- **How:** `tenant_<slug>` schemas (or DBs), Flyway runs per schema,
  request routing picks the schema.
- **Pros:** strongest blast-radius isolation; per-tenant backup/restore;
  natural fit if enterprise customers later demand data residency.
- **Cons:** migration fan-out, connection-pool fragmentation, cross-tenant
  reporting becomes ETL, onboarding complexity — heavy operational cost for
  single-digit tenant counts.

## Decision

**Stay on A.** At our scale the failure mode we must prevent is an unscoped
query, and that is already covered by per-module tenant tests plus the
service-level `TenantContext.require()` discipline. Revisit when either
trigger fires: **50 active tenants** (operational argument for C) or **any
isolation incident** (immediate argument for B as defense-in-depth, without
abandoning A).

## Consequences

- Every new table MUST carry `organization_id` + FK, every new repository
  method MUST scope by tenant, every new module MUST add a tenant-isolation
  test — enforced in review, not by tooling.
- If B is later adopted, it layers *under* the current checks (policies as a
  safety net), not as a replacement.
- Related hardening already in place: refresh-token rotation with reuse
  detection (`refresh_tokens`), invite tokens stored as hashes, platform role
  grant restricted to platform admins.
