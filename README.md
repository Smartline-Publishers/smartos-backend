# SmartOS Backend

Spring Boot 4 REST API for SmartOS, the publishing management platform for Smartline Publishers Ltd. Modular monolith, shared-schema multi-tenancy (`organization_id`), JWT auth, Flyway migrations, OpenAPI docs.

Sibling repo: `../smartos` (Next.js frontend, consumes this API). System design, schema, conventions, and program roadmap live in `docs/`.

## Stack

- Java 21 bytecode (runs on JDK 21+; verified on JDK 25), Maven wrapper (`mvnw` / `mvnw.cmd` — no local Maven needed)
- Spring Boot 4.1: Web, Data JPA, Security + JJWT, Validation, AOP (audit), Flyway, PostgreSQL driver, Springdoc OpenAPI
- Spring Mail (optional password-reset email) and server-verified WebAuthn/FIDO2 passkeys
- Tests: JUnit 5 + Mockito (`src/test`)

## Prerequisites

- JDK 21+
- Docker + Docker Compose (local Postgres only; the API itself runs locally)
- A local `.env` file (never committed — company policy excludes all env files
  from git). Create it with these variables (see Configuration below for details):

```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=smartos
POSTGRES_USER=smartos
POSTGRES_PASSWORD=<strong password>
DB_URL=jdbc:postgresql://localhost:5432/smartos
DB_USER=smartos
DB_PASSWORD=<same strong password>
JWT_SECRET=<output of: openssl rand -base64 48>
CORS_ORIGINS=http://localhost:3000
PGADMIN_PASSWORD=<any local value>
```

## Quickstart

```bash
# 1. Start Postgres (compose reads .env in this directory)
docker compose up db -d

# 2. Run the API — the script loads .env, validates it, then starts Maven.
# (Spring Boot reads process env, not .env files, so export manually only
# if you are not using the script.)
.\run-local.ps1

| Service  | URL                            |
|----------|--------------------------------|
| API      | http://localhost:8080/api/v1   |
| OpenAPI  | http://localhost:8080/swagger-ui.html |
| Postgres | localhost:5432                 |

First org + admin: `POST /api/v1/auth/register-organization`, then `POST /api/v1/auth/login`.

## Layout

```text
./                          # this repo
├── docker-compose.yml      # local Postgres + pgAdmin
├── .env                    # local only, never committed (see Prerequisites)
├── docs/                   # ARCHITECTURE, DATABASE, API_CONVENTIONS, AUTHENTICATION, ROADMAP, ADR-001
├── mvnw / mvnw.cmd         # Maven wrapper (downloads Maven on first use)
├── run-local.ps1           # loads .env, validates, starts the API
├── pom.xml
└── src/
    ├── main/java/com/smartline/smartos/
    │   ├── config/         # OpenAPI, CORS, JPA auditing, Jackson
    │   ├── common/         # ApiResponse envelope, PageResponse, exceptions, paging
    │   ├── tenant/         # TenantContext, JWT/header interceptor
    │   ├── security/       # JwtService, filter, roles, auth DTOs
    │   └── modules/        # organization, user, book, manuscript, task,
    │                       # production, inventory, sales, finance, billing,
    │                       # audit, reporting, dashboard
    ├── main/resources/     # application.yml, db/migration/V1-V12
    └── test/               # service, tenant-isolation, web-slice, contract tests
```

## Configuration

| Var | Used by | Default |
| --- | ------- | ------- |
| `POSTGRES_HOST` / `POSTGRES_PORT` | connection host/port as seen by the API (`localhost` local; `db` if containerized) | `localhost/5432` (port also mapped by compose) |
| `POSTGRES_DB/USER/PASSWORD` | compose `db` container | `smartos/smartos/<required>` |
| `DB_URL` | `application.yml` datasource (keep in sync with the parts above) | `jdbc:postgresql://localhost:5432/smartos` |
| `DB_USER` / `DB_PASSWORD` | datasource credentials | `postgres/postgres` (override in `.env`) |
| `JWT_SECRET` | token signing — standard Base64, URL-safe Base64, or a raw passphrase of ≥32 bytes; generate with `openssl rand -base64 48` | dev-only placeholder — must override (export `JWT_SECRET` in your shell; never commit it) |
| `CORS_ORIGINS` | allowed web origins | `http://localhost:3000` |
| `APP_FRONTEND_URL` | public web origin used in password-reset links | `http://localhost:3000` |
| `MAIL_ENABLED` + `SPRING_MAIL_*` | optional SMTP delivery for reset emails | disabled/empty |
| `PASSWORD_RESET_EXPOSE_DEV_TOKEN` | dev-only: return the reset token in the API response | `false` |
| `TRUST_FORWARDED_HEADERS` | use `X-Forwarded-For` behind a trusted proxy | `false` |
| `WEBAUTHN_RP_ID` / `WEBAUTHN_ALLOWED_ORIGINS` | passkey RP ID and exact allowed origins | `localhost` / `http://localhost:3000` |
| `WEBAUTHN_RP_NAME` / `WEBAUTHN_USER_VERIFICATION` | authenticator display name / `preferred` or `required` | `SmartOS` / `preferred` |

Never commit `.env`. `ddl-auto` is `validate`: schema changes go through `src/main/resources/db/migration/V<seq>__<name>.sql` (forward-only).

## Authentication, password recovery and passkeys

See [`docs/AUTHENTICATION.md`](docs/AUTHENTICATION.md) for the full flow,
security properties, SMTP/WebAuthn setup, migration notes and deployment
checklist. In short:

- `POST /api/v1/auth/password/forgot`, `/password/reset/validate` and
  `/password/reset` are public and rate limited; reset tokens are hashed,
  single-use and expire after 30 minutes.
- `POST /api/v1/users/me/password` verifies the current password and revokes
  every other session (optionally keeping the current one).
- Passkeys are registered/managed under `/users/me/passkeys` and used to sign in
  via `/auth/passkeys/options` + `/auth/passkeys/verify`; all challenges,
  origins, RP IDs, flags and signatures are verified server-side.
- Production requires HTTPS and a real `WEBAUTHN_RP_ID`; `localhost` is the only
  permitted insecure origin.

## Secret rotation

- **JWT_SECRET:** change the value and restart — all previously issued tokens
  invalidate immediately (stateless verification against the new key). No DB change.
- **DB_PASSWORD:** `ALTER USER smartos WITH PASSWORD '<new>'` inside Postgres,
  then update `.env` (`POSTGRES_PASSWORD` + `DB_PASSWORD`) and restart the API.
  Keep both in sync or the container and the app disagree.
- CORS origins are an allowlist: production deployments must set
  `CORS_ORIGINS` to the exact frontend origin(s); the localhost default is dev-only.

## Billing stub → real Stripe

`POST /api/v1/billing/webhooks/stripe` accepts provider-shaped JSON today
(`{id, type, data: {organizationId, plan}}`) with no signature check — a stand-in
for local development. To go live:

1. Add the Stripe SDK and replace the payload parsing with
   `Webhook.constructEvent(payload, sigHeader, webhookSecret)`.
2. Set `STRIPE_WEBHOOK_SECRET` (env, never committed) and verify the signature
   **before** any processing; reject mismatches with 400.
3. Keep the `billing_events` idempotency check exactly where it is — retried
   deliveries must stay no-ops.
4. Keep mapping event types to plan changes in `BillingService`; the controller
   contract (`{received, duplicate, plan}`) is unchanged.

## Testing

```bash
./mvnw test   # unit + slice tests (H2-backed, no Postgres needed)
```

## Conventions

- Controllers thin (`@PreAuthorize` + `@Valid` DTOs); rules live in `@Transactional` services; repositories scope every query by `organizationId`; entities never leave the API (DTOs only). See `docs/API_CONVENTIONS.md`.
- No Lombok (annotation processor doesn't run on the JDK 25 toolchain — accessors are handwritten). ByteBuddy is pinned above Boot's default for Mockito on JDK 25. See `docs/ARCHITECTURE.md`.
