# SmartOS Backend

Spring Boot 4 REST API for SmartOS, the publishing management platform for Smartline Publishers Ltd. Modular monolith, shared-schema multi-tenancy (`organization_id`), JWT auth, Flyway migrations, OpenAPI docs.

Sibling repo: `../smartos` (Next.js frontend, consumes this API). System design, schema, conventions, and program roadmap live in `docs/`.

## Stack

- Java 21 bytecode (runs on JDK 21+; verified on JDK 25), Maven wrapper (`mvnw` / `mvnw.cmd` — no local Maven needed)
- Spring Boot 4.1: Web, Data JPA, Security + JJWT, Validation, AOP (audit), Flyway, PostgreSQL driver, Springdoc OpenAPI
- Tests: JUnit 5 + Mockito (`src/test`)

## Prerequisites

- JDK 21+
- Docker + Docker Compose (local Postgres only; the API itself runs locally)
- Copy `.env.example` → `.env` and set `POSTGRES_PASSWORD`, `DB_PASSWORD` (same value), and `JWT_SECRET` (≥32 random bytes, e.g. `openssl rand -base64 32`)

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
├── docker-compose.yml      # local Postgres (db service only)
├── .env.example            # copy to .env (never commit .env)
├── docs/                   # ARCHITECTURE, DATABASE, API_CONVENTIONS, ROADMAP
├── mvnw / mvnw.cmd         # Maven wrapper (downloads Maven on first use)
├── pom.xml
└── src/
    ├── main/java/com/smartline/smartos/
    │   ├── config/         # OpenAPI, CORS, JPA auditing, Jackson
    │   ├── common/         # ApiResponse envelope, PageResponse, exceptions
    │   ├── tenant/         # TenantContext, JWT/header interceptor
    │   ├── security/       # JwtService, filter, roles, auth DTOs
    │   └── modules/        # organization, user (auth), book, audit
    ├── main/resources/     # application.yml, db/migration/V1-V3
    └── test/               # service + tenant-isolation + web-slice tests
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

Never commit `.env`. `ddl-auto` is `validate`: schema changes go through `src/main/resources/db/migration/V<seq>__<name>.sql` (forward-only).

## Testing

```bash
./mvnw test   # unit + slice tests (H2-backed, no Postgres needed)
```

## Conventions

- Controllers thin (`@PreAuthorize` + `@Valid` DTOs); rules live in `@Transactional` services; repositories scope every query by `organizationId`; entities never leave the API (DTOs only). See `docs/API_CONVENTIONS.md`.
- No Lombok (annotation processor doesn't run on the JDK 25 toolchain — accessors are handwritten). ByteBuddy is pinned above Boot's default for Mockito on JDK 25. See `docs/ARCHITECTURE.md`.
