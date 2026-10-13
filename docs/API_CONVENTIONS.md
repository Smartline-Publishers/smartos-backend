# API Conventions

## Base & Resources

- Base path: `/api/v1`. Resources are plural kebab-case:
  `/api/v1/books`, `/api/v1/book-contributors`, `/api/v1/manuscripts`,
  `/api/v1/production-jobs`, `/api/v1/print-runs`, `/api/v1/stock-movements`,
  `/api/v1/orders`, `/api/v1/transactions`, `/api/v1/royalty-statements`.
- Verbs: `GET` (list/one), `POST` (create → `201` + `Location`), `PATCH` (partial),
  `PUT` (full replace, rare), `DELETE` (→ `204`, soft-delete where audited).
- Nested reads OK (`GET /books/{id}/manuscripts`); writes go to top-level resource.

## DTOs — Never Entities

- Controllers accept/return DTOs only (`*Request`, `*Response`); entities never leak
  (no lazy-loading surprises, no `password_hash`, stable contract).
- Bean Validation on requests (`@NotBlank`, `@ISBN`, `@Positive`); map violations
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

- Query: `?page=0&size=20&sort=title,asc&status=PUBLISHED&q=...`.
- Cap `size ≤ 100`. Unknown sort fields → `400 INVALID_SORT`.
- Filter names match DTO fields; `q` is free-text on whitelisted columns only.

## Validation & Status Codes

| Case | Code | `error.code` |
|---|---|---|
| OK / created / no-content | 200 / 201 / 204 | — |
| Validation failure | 400 | `VALIDATION_ERROR` + `fields` map |
| Unauthenticated | 401 | `UNAUTHORIZED` |
| Forbidden (role/org) | 403 | `FORBIDDEN` |
| Not found (or other org's row) | 404 | `NOT_FOUND` (never 403 on IDOR — avoid oracle) |
| Conflict (dup ISBN/email) | 409 | `CONFLICT` |
| Unprocessable state transition | 422 | `INVALID_TRANSITION` |

## Auth & RBAC

- `Authorization: Bearer <access-JWT>` on all `/api/v1/**` except
  `POST /auth/login`, `POST /auth/refresh`, `/v3/api-docs`, `/swagger-ui/**`.
- JWT claims: `sub=userId`, `orgId`, `role`, `exp` (short TTL ~15m).

| Area | ADMIN | EDITOR | PRODUCTION | SALES | FINANCE | VIEWER |
|---|---|---|---|---|---|---|
| users/org | RW | R | — | — | — | — |
| books/manuscripts/tasks | RW | RW | R | R | R | R |
| production/inventory | RW | R | RW | — | R | R |
| orders/customers | RW | — | — | RW | R | R |
| transactions/budgets/contracts/royalties | RW | — | — | R | RW | R |
| reporting | RW | R | R | R | R | R |

Enforced with `@PreAuthorize("hasRole(...)")` + org check in service.

## Versioning & Docs

- URL versioning (`/api/v1`); breaking change → `/api/v2`, keep v1 ≥ 2 releases.
- Additive changes (new fields/endpoints) are minor, no version bump.
- OpenAPI: `/v3/api-docs` (JSON) + `/swagger-ui.html`; annotations required on
  every controller (`@Operation`, `@ApiResponse`, `@Parameter`).
- Frontend `features\*\api.ts` + `types\` must be regenerated/hand-synced on
  any OpenAPI change; CI should fail on drift (contract test or lint diff).
