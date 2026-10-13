# Architecture

## Backend — Modular Monolith (this repo)

Spring Boot 4 / Java 21 bytecode (runs on JDK 21+; CI/dev verified on JDK 25).
One deployable, internally split by domain module.

```text
src\main\java\com\smartline\smartos\
├── SmartOsApplication.java
├── config\        # Security, CORS, JPA, OpenAPI, Jackson, Flyway
├── security\      # JwtFilter, JwtService, TenantContext, CurrentOrg
├── exception\     # ApiExceptionHandler, ErrorCode, field-error mapping
├── tenant\        # TenantContext holder, org-scoping helpers/interceptor
├── common\        # PageResponse, ApiResponse envelope, auditing base
└── modules\
    ├── auth\      # login/me/refresh (stateless JWT)
    ├── users\
    ├── organizations\
    ├── books\
    ├── manuscripts\
    ├── tasks\
    ├── production\
    ├── inventory\
    ├── sales\
    ├── finance\
    ├── contracts\
    └── reporting\
```

Each module follows: `controller → service → repository → entity`, with
`dto\` (Request/Response, never expose entities) + `mapper\` + `validation`.

Conventions:

- Controllers: thin, return envelope, `@Valid` DTOs, `@PreAuthorize` roles.
- Services: business rules + transactions (`@Transactional`), org-scoping checks.
- Repositories: Spring Data JPA; every query filters by `organizationId`.
- Entities: JPA + Hibernate Envers or manual `audit_logs` write on mutation.
- Migrations: Flyway `V<seq>__<name>.sql`, forward-only, no auto-DDL in prod.

## Frontend — Next.js App Router (sibling repo `..\smartos`)

```text
..\smartos\
├── app\           # routes: (auth)\login, dashboard, books, manuscripts, ...
├── components\    # shared UI (Table, Forms, Dialog, EmptyState)
├── features\      # per-domain: books\, manuscripts\, sales\, ... (api.ts, hooks, components)
├── lib\           # api-client (fetch + JWT attach), auth-provider, query keys
├── hooks\         # useAuth, usePagination, useTenant
└── types\         # DTO mirrors of backend contracts (generated or hand-synced)
```

Rules: feature modules own their API calls/types; `lib\api-client` is the only
place that knows base URL + token attach + envelope unwrap + 401 handling;
server components for read-heavy pages, client components for forms/tables.

## Multi-Tenancy — Shared Schema

- Every business table: `organization_id UUID NOT NULL REFERENCES organizations(id)`
  + index `(organization_id, ...)`; unique constraints scoped to org
  (e.g. `UNIQUE (organization_id, isbn)`).
- Request flow: `Authorization: Bearer <JWT>` → `JwtFilter` validates →
  extracts `orgId` + `role` → sets `TenantContext` (ThreadLocal/request-scoped) →
  services enforce `entity.organizationId == TenantContext.orgId`.
- Defense in depth: row filter in queries AND service-level assert AND FK scoping.
- Audit: `audit_logs` records `(org_id, actor_id, entity, entity_id, action, diff)`.

## Cross-Cutting

- REST under `/api/v1`, kebab-case resources, DTO-only payloads, Bean Validation,
  paginated lists (`page/size/sort` → `{data, meta}` envelope). See `API_CONVENTIONS.md`.
- Errors: `{error: {code, message, fields?}}` with stable codes.
- OpenAPI generated from annotations; frontend types must match it.

## Trade-Offs

| Decision | Now | Later | Why |
|---|---|---|---|
| Modular monolith | ✅ single deploy | Extract hot modules (reporting/billing) to services | Speed now, seams preserved via module boundaries |
| Shared-schema tenancy | ✅ `organization_id` column | RLS policies or schema-per-tenant | Simplest correct isolation; RLS/schema add ops cost prematurely |
| Stateless JWT | ✅ no session store | Refresh-token rotation store if needed | Stateless scales; short TTL + refresh mitigates theft window |
| Flyway SQL | ✅ explicit migrations | — (keep) | Reviewable, reproducible; no magic DDL drift |
| No Lombok | ✅ handwritten accessors | Revisit if toolchain pins JDK LTS | Lombok's annotation processor didn't run on the JDK 25 toolchain; explicit code has zero magic |
| ByteBuddy 1.18.x | ✅ pinned above Boot default | Drop pin when Boot manages a JDK-25-ready version | Mockito class-mocking needs a ByteBuddy that supports the runtime JDK |
