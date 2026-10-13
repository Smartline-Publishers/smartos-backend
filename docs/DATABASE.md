# Database

PostgreSQL 16. Flyway-managed (`V1__init.sql`, then one file per change).
All FKs indexed. Money as `NUMERIC(12,2)`. IDs as `UUID` (gen_random_uuid).

## Core Entities & Relationships

- **organizations** `(id, name, slug UNIQUE, plan, created_at)` — tenant root.
- **users** `(id, org_id FK, email, password_hash, role, active)` —
  `UNIQUE (org_id, email)`; roles: `ADMIN, EDITOR, PRODUCTION, SALES, FINANCE, VIEWER`.
- **books** `(id, org_id FK, title, isbn, format, status, pub_date, price)` —
  `UNIQUE (org_id, isbn)`; status: `DRAFT → IN_PRODUCTION → PUBLISHED → ARCHIVED`.
- **contributors** `(id, org_id FK, name, type, email)` — `AUTHOR | ILLUSTRATOR | EDITOR | TRANSLATOR`.
- **book_contributors** `(book_id FK, contributor_id FK, role, royalty_share)` —
  join with payload; one row per (book, contributor, role).
- **manuscripts** `(id, org_id FK, book_id FK, current_stage, file_url, version)` +
  **manuscript_stages** `(id, manuscript_id FK, stage, actor_id, at, note)` —
  stages: `SUBMITTED → REVIEW → EDITING → PROOF → APPROVED → ARCHIVED`.
- **tasks** `(id, org_id FK, book_id FK NULL, manuscript_id FK NULL, assignee_id FK, title, status, due_at)` —
  status: `TODO → DOING → REVIEW → DONE`.
- **production_jobs** `(id, org_id FK, book_id FK, type, vendor, qty_planned, qty_actual, status, due_at)` —
  type: `PRINT | BIND | DESIGN | EBOOK`; status: `PLANNED → IN_PROGRESS → QC → COMPLETE | CANCELLED`.
- **print_runs** `(id, job_id FK, qty, unit_cost, run_at)` — immutable once posted.
- **warehouses** `(id, org_id FK, name, location)`; **stock_movements**
  `(id, org_id FK, book_id FK, warehouse_id FK, qty_delta, reason, ref_id)` —
  `IN | OUT | ADJUST | TRANSFER`; current stock = `SUM(qty_delta)` per (book, warehouse).
- **customers** `(id, org_id FK, name, type, email)` — `RETAIL | WHOLESALE | DISTRIBUTOR`.
- **orders** `(id, org_id FK, customer_id FK, status, total, placed_at)` +
  **order_items** `(order_id FK, book_id FK, qty, unit_price)` —
  status: `DRAFT → CONFIRMED → FULFILLED → INVOICED | CANCELLED`.
- **transactions** `(id, org_id FK, kind, amount, order_id FK NULL, at, memo)` —
  kind: `SALE | REFUND | EXPENSE | ROYALTY_PAYOUT`; refunds reference original order.
- **budgets** `(id, org_id FK, book_id FK NULL, period, planned, actual)` — per title or org-wide.
- **contracts** `(id, org_id FK, book_id FK, contributor_id FK, signed_at, terms)` +
  **royalty_rules** `(contract_id FK, basis, rate, tiers JSONB)` —
  basis: `NET | GROSS | UNITS`; **royalty_statements**
  `(id, contract_id FK, period, gross, net, payable, status)` —
  status: `DRAFT → APPROVED → PAID`.
- **audit_logs** `(id, org_id FK, actor_id FK, entity, entity_id, action, diff JSONB, at)` —
  `CREATE | UPDATE | DELETE | STATUS`; append-only (no UPDATE/DELETE grants).

## Business Rules

1. **ISBN unique per org:** `UNIQUE (org_id, isbn)`; validate format before insert.
2. **Royalty calc from net sales:** `net = gross − refunds − discounts − fees`;
   `payable = net × rate` (tiered rules evaluated highest-applicable tier);
   statements generated per (contract, period), `DRAFT` until finance approves.
3. **Stock movements transactional:** order fulfilment + stock decrement +
   transaction row in one DB transaction; `qty` never drives negative stock
   without `ADJUST` reason + admin role; `print_runs` append stock via `IN` movement.
4. **Workflow guards:** manuscript stage transitions validated server-side
   (no skips except `ARCHIVED` from any stage by ADMIN/EDITOR); tasks require
   assignee in same org; orders only fulfilled with sufficient stock.
5. **Money:** all calcs in `NUMERIC`, round half-up to 2dp at statement level only.

## Tenant-Isolation Rule (mandatory)

- Every business table **MUST** have `organization_id UUID NOT NULL REFERENCES organizations(id)`.
- Every such table **MUST** have an index leading with `organization_id`.
- Every `UNIQUE` **MUST** be scoped: `(organization_id, <natural key>)` — never global.
- Every repository query **MUST** filter `WHERE organization_id = :orgId`
  (TenantContext-supplied; never trust client-sent `orgId`).
- FKs that cross tables (e.g. `order_items → books`) must be validated to belong
  to the same org in service code before persist.
