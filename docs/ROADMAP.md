# Roadmap — Phases 0–6

## Phase 0 — Foundation (this stage) ✅ scope

- [ ] Backend repo: `README.md`, `docker-compose.yml`, `.env.example`, `.gitignore`, `docs\`
- [ ] Backend skeleton boots: health endpoint, OpenAPI, Flyway empty baseline, JWT filter stub
- [ ] Frontend shell boots: App Router layout, login page stub, api-client stub

**Acceptance:** `docker compose up db` healthy; backend `GET /v3/api-docs` 200;
frontend `npm run dev` renders; docs 4 files present in the backend repo;
backend and frontend evolve in their own repos (`smartos-backend\`, `smartos\`).

## Phase 1 — Auth / Users / Books

- JWT login/refresh/me, password hashing (bcrypt), org-seeded admin
- Users CRUD + roles (ADMIN-gated), org switch guard (single-org token v1)
- Books CRUD + contributors + `book_contributors`; ISBN-per-org unique; audit logs
- Frontend: login flow, auth guard, books list/detail/create pages

**Acceptance:** login → token contains `orgId+role`; cross-org fetch returns 404;
dup ISBN same org → 409, different org → 201; audit row per mutation; paginated
`GET /api/v1/books` envelope + OpenAPI documented.

## Phase 2 — Manuscripts / Editorial + Tasks

- Manuscripts + stage machine (`SUBMITTED→REVIEW→EDITING→PROOF→APPROVED`), guards
- File ref (`file_url` stub, no binary store yet), version increment on edit
- Tasks CRUD with assignee/due/status, links to book/manuscript
- Frontend: manuscript board (stage columns), task list with filters

**Acceptance:** illegal skip `SUBMITTED→APPROVED` → 422; version bumps on content
edit; tasks filtered by `?status&assigneeId`; stage history persisted.

## Phase 3 — Production / Inventory

- Production jobs lifecycle + print runs (immutable once posted)
- Warehouses + stock movements; `SUM(qty_delta)` stock view; transfer support
- Print run posting auto-creates `IN` movement in same transaction
- Frontend: jobs Kanban, stock-by-book/warehouse table, low-stock flag

**Acceptance:** posting run adjusts stock atomically (fail → rollback);
negative stock blocked without `ADJUST`+ADMIN; stock history auditable per ref.

## Phase 4 — Sales / Finance / Royalties

- Customers, orders + items (totals server-computed), fulfil → stock `OUT` + transaction
- Transactions (`SALE/REFUND/EXPENSE/ROYALTY_PAYOUT`), budgets planned vs actual
- Contracts + royalty rules + statement generation from net sales; approve/pay flow
- Frontend: order create/fulfil screens, finance ledger, royalty statement view

**Acceptance:** order total ignores client math; fulfil decrements stock + writes
`SALE` txn in one tx; `payable = net × rate` verified by seeded fixture;
statement `DRAFT→APPROVED→PAID` only forward; refunds reduce `net`.

## Phase 5 — Dashboard / Reporting + Hardening

- Reporting endpoints: sales by title/period, stock valuation, royalty liability,
  editorial throughput; indexed queries, `size≤100` enforced
- Dashboard page (KPIs + charts), CSV export, audit-log viewer
- Hardening: rate limits on auth, CORS lockdown, secret rotation docs,
  pagination/perf pass, 401/403/404 semantics audit

**Acceptance:** p95 list endpoint < 300ms on seed 10k rows; CSV matches API totals;
no entity leak in any response; OpenAPI covers 100% of routes; `mvn test` + `npm build` green.

## Phase 6 — SaaS Billing / Multi-Org Self-Serve

- Public signup → creates org + ADMIN user; org invite flow; plan field enforced
- Stripe (or equivalent) subscription stub: `plan`, seat limits, webhook handler
- Per-org feature flags / quotas; onboarding wizard in frontend
- RLS or schema-per-tenant spike (decision record), refresh-token rotation store

**Acceptance:** new signup gets isolated org (no data leak); plan downgrade blocks
over-quota creates with 403 `PLAN_LIMIT`; billing webhook idempotent; ADR filed
for tenancy next step.
