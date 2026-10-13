# Roadmap — Phases 0–6

## Phase 0 — Foundation (this stage) ✅ scope

- [ ] Backend repo: `README.md`, `docker-compose.yml`, local `.env` (per README), `.gitignore`, `docs\`
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

## Phase 2 — Manuscripts / Editorial + Tasks ✅ implemented

- Manuscripts + stage machine (`SUBMITTED→REVIEW→EDITING→PROOF→APPROVED`), guards
- File ref (`file_url` stub, no binary store yet), version increment on edit
- Tasks CRUD with assignee/due/status, links to book/manuscript
- Frontend: manuscript board (stage columns), task list with filters

**Acceptance:** illegal skip `SUBMITTED→APPROVED` → 400 with message; version bumps on content
edit; tasks filtered by `?status&assigneeId`; stage history persisted (`manuscript_events`).

## Phase 3 — Production / Inventory ✅ implemented

- Production jobs lifecycle + print runs (immutable once posted)
- Warehouses + stock movements; `SUM(qty_delta)` stock view; transfer support
- Print run posting auto-creates `IN` movement in same transaction
- Frontend: jobs Kanban, stock-by-book/warehouse table, low-stock flag

**Acceptance:** posting run adjusts stock atomically (fail → rollback);
negative stock blocked without `ADJUST`+ADMIN; stock history auditable per ref.

## Phase 4 — Sales / Finance / Royalties ✅ implemented

- Customers, orders + items (totals server-computed), fulfil → stock `OUT` + transaction
- Transactions (`SALE/REFUND/EXPENSE/ROYALTY_PAYOUT`), budgets planned vs actual
- Contracts + royalty rules + statement generation from net sales; approve/pay flow
- Frontend: order create/fulfil screens, finance ledger, royalty statement view
- Also closed review gaps: book↔contributor link endpoints (royalties need them)

**Acceptance:** order total ignores client math; fulfil decrements stock + writes
`SALE` txn in one tx; `payable = net × rate` verified by seeded fixture;
statement `DRAFT→APPROVED→PAID` only forward; refunds reduce `net`.

## Phase 5 — Dashboard / Reporting + Hardening ✅ implemented

- Reporting endpoints: sales by title/period (+CSV), royalty liability,
  editorial throughput; `size≤100` enforced on all lists; reporting indexes (V9)
- Live `GET /api/v1/dashboard/summary` (KPIs, pipelines, attention, activity,
  trends); audit-log viewer (`GET /api/v1/audit-logs`, admin-only)
- Hardening: CORS single-authority, envelope 401/403/404 audit, bad-sort 400,
  secret-rotation docs, immutable ledgers/statements
- Frontend: live dashboard mapping, `/reports`, `/audit`

**Acceptance:** CSV totals match API totals (tested); no entity leak in any response;
OpenAPI auto-covers all routes; `mvn test` + `npm build` green.
Not verified here: p95 <300ms on a 10k-row seed (needs a perf harness + live DB);
auth rate limiting is deferred to the deployment gateway (documented decision).

## Phase 6 — SaaS Billing / Multi-Org Self-Serve ✅ implemented

- Public signup → creates org + ORG_ADMIN (`POST /auth/register-organization`,
  `/register` wizard); invite flow (`/invites`, accept → auto-login); plan enforced
- Stripe subscription stub (`/billing/webhooks/stripe`, idempotent by event id):
  `plan` mapping, seat/book quotas (`FREE 5/100`, `GROWTH 50/2000`, SCALE open)
- Per-org settings JSONB already the flags mechanism; quotas surfaced in
  `GET /organizations/current`; onboarding = register wizard + invite accept
- RLS vs schema-per-tenant: `docs/ADR-001-tenant-isolation.md` (stay shared-schema;
  triggers: 50 tenants or any incident); refresh-token rotation store with reuse
  detection; platform-role grant restricted

**Acceptance:** new signup gets isolated org (tenant tests + bootstrap test);
plan downgrade blocks over-quota creates with 403 `PLAN_LIMIT` (tested for users;
books); billing webhook idempotent (tested); ADR filed.
