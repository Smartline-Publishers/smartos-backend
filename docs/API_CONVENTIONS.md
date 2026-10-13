# API Conventions

## Base & Resources

- Base path: `/api/v1`. Resources are plural kebab-case:
  `/api/v1/books` (+ `/{id}/contributors`), `/api/v1/contributors`,
  `/api/v1/manuscripts` (+ `/{id}/transitions`), `/api/v1/tasks`,
  `/api/v1/production/jobs` (+ `/{id}/runs`, `/runs/{id}/post`),
  `/api/v1/warehouses`, `/api/v1/inventory/movements|levels|transfers`,
  `/api/v1/customers`, `/api/v1/orders` (+ `/{id}/confirm|fulfil|cancel`),
  `/api/v1/finance/transactions|budgets`,
  `/api/v1/royalties/contracts|statements`, `/api/v1/reports/*`,
  `/api/v1/dashboard/summary`, `/api/v1/audit-logs`, `/api/v1/users`,
  `/api/v1/organizations`, `/api/v1/auth`.
- Verbs: `GET` (list/one), `POST` (create → `201`; actions like `/confirm`, `/fulfil`,
  `/post`, `/generate`, `/approve`, `/pay`, `/terminate`, `/transitions`), `PATCH`
  (partial), `DELETE` (→ `204`; hard delete guarded by history checks, with an
  audit event — no soft-delete).
- Nested writes live under the owning resource (`POST /books/{id}/contributors`,
  `POST /production/jobs/{id}/runs`); cross-module effects (posting, fulfil)
  happen server-side in one transaction.

## DTOs — Never Entities

- Controllers accept/return DTOs only (`*Request`, `*Response`); entities never leak
  (no lazy-loading surprises, no `password_hash`, stable contract).
- Bean Validation on requests (`@NotBlank`, `@NotNull`, `@Size`, `@Min`,
  `@Email`, `@DecimalMin`, `@DecimalMax`); map violations
  to `400` field errors. IDs in path are UUID strings.

## Envelopes

```jsonc
// Success (single + collection)
{ "data": { "id": "...", "title": "..." } }
{ "data": [ ... ], "meta": { "page": 0, "size": 20, "totalElements": 127, "totalPages": 7 } }

// Error
{ "error": { "code": "VALIDATION_ERROR", "message": "isbn is invalid", "fields": { "isbn": "must be 13 digits" } } }
```

- No bare arrays or entity graphs. `meta` required on every paginated list.
- `GET` list defaults: `page=0&size=20&sort=createdAt,desc`.

## Pagination / Filtering / Sorting

- Query: `?page=0&size=20&sort=title,asc&status=PUBLISHED&q=...`
  (Spring defaults: page `0`, size `20`, unsorted).
- Every list endpoint clamps `size` to ≤ 100 (`Paging.capped`); the echoed
  `meta.size` shows the applied value. Unknown sort fields → `400 INVALID_SORT`.
- Filter names match DTO fields; `q`/`search` is free-text on whitelisted columns only.

## Validation & Status Codes

| Case | Code | `error.code` |
|---|---|---|
| OK / created / no-content | 200 / 201 / 204 | — |
| Validation failure | 400 | `VALIDATION_ERROR` + `fields` map |
| Unauthenticated | 401 | `UNAUTHORIZED` |
| Forbidden (role/org) | 403 | `FORBIDDEN` |
| Not found (or other org's row) | 404 | `NOT_FOUND` (never 403 on IDOR — avoid oracle) |
| Conflict (dup ISBN/email) | 409 | `CONFLICT` |
| Illegal transition or state violation | 400 | `BAD_REQUEST` with message |

## Auth & RBAC

- `Authorization: Bearer <access-JWT>` on all `/api/v1/**` except
  `POST /auth/login`, `POST /auth/refresh`, `POST /auth/register-organization`,
  `/v3/api-docs`, `/swagger-ui/**`. Missing/invalid tokens → `401` envelope
  from the security chain; role failures → `403`; cross-tenant IDs → `404`.
- JWT claims: `sub=userId`, `orgId`, `role`, `exp` (short TTL ~15m).

| Area | ADMIN | EDITOR | PRODUCTION | SALES | FINANCE | VIEWER |
|---|---|---|---|---|---|---|
| users/org | RW | R | — | — | — | — |
| books/manuscripts/tasks | RW | RW | R | R | R | R |
| production/inventory | RW | R | RW | — | R | R |
| orders/customers | RW | — | — | RW | R | R |
| transactions/budgets/contracts/royalties | RW | — | — | R | RW | R |
| reporting | RW | R | R | R | R | R |

Enforced with `@PreAuthorize("hasAnyRole(...)")` + org check in service.

## Versioning & Docs

- URL versioning (`/api/v1`); breaking change → `/api/v2`, keep v1 ≥ 2 releases.
- Additive changes (new fields/endpoints) are minor, no version bump.
- OpenAPI: `/v3/api-docs` (JSON) + `/swagger-ui.html`, generated from
  controllers by default; add `@Operation` summaries on non-obvious endpoints.
- Frontend `features\*\api.ts` + `types\` must be regenerated/hand-synced on
  any OpenAPI change; CI should fail on drift (contract test or lint diff).
