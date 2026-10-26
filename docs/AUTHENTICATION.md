# Authentication, password recovery and passkeys

This document covers SmartOS account security: sign-in, password rules,
password recovery, password changes, passkeys (WebAuthn/FIDO2), token
revocation and the configuration/deployment requirements for each.

All endpoints live under `/api/v1` and use the standard `ApiResponse`
envelope (`docs/API_CONVENTIONS.md`).

---

## 1. Sign-in

`POST /api/v1/auth/login`

```json
{ "email": "admin@example.com", "password": "…", "organizationId": null }
```

- Email is trimmed and matched case-insensitively. If the same email exists in
  several organizations, the server checks each account's password and signs in
  to the matching one. It only returns `AMBIGUOUS_TENANT` when the *same*
  password matches more than one account, in which case the client shows an
  organization picker.
- Unknown accounts still run a dummy BCrypt comparison, and every failure
  returns the same `Invalid credentials` message. This prevents account
  enumeration and timing leaks.
- Failures are rate limited (8 failures per email + IP per 10 minutes by
  default). Successful login clears the bucket.
- If TOTP is enabled the response contains a short-lived `challengeToken`
  instead of an access token; complete it with `POST /auth/2fa/verify`.
- Successful login returns an access token (15 minutes) and a rotating refresh
  token (7 days). Only SHA-256 hashes of refresh tokens are stored.
- Logout (`POST /auth/logout`) revokes the presented refresh token.

### Why "Invalid credentials" can appear even with the right password

1. **A stale access token in `localStorage`.** The web client attaches a Bearer
   token to authenticated calls. Tokens are validated on load and expired ones
   are discarded. Public auth calls (`/auth/login`, `/auth/password/*`,
   `/auth/passkeys/*`, `/auth/register-organization`) must never send it; the
   client now omits it explicitly.
2. **An old backend process.** After deploying auth changes, make sure the
   process listening on port 8080 is the new build. A stale process can reject
   new public endpoints with 401.
3. **A service worker intercepting API calls.** `public/sw.js` must not handle
   `/api/*` or cross-origin requests. The current worker only caches static
   assets and uses the `smartos-v2` cache name.
4. **Wrong organization with a shared email.** Different organizations can have
   different passwords for the same email; pass `organizationId` when prompted.

---

## 2. Password rules

Rules are enforced server-side by `PasswordPolicy` and mirrored in the UI by
`src/lib/password.ts`:

- Minimum 10 characters; maximum 72 bytes (BCrypt's effective limit).
- Must contain at least one letter and at least one number **or** symbol.
- Rejects common/breached values, all-digit passwords, two-character
  repetition and simple ascending/descending runs.
- Rejects the account's email local part (when it is at least 4 characters).
- Passwords are never trimmed or silently truncated.

These rules apply to registration, password reset and password change.

---

## 3. Forgot / reset password

The flow is fully unauthenticated. `SecurityConfig` permits all three endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/password/forgot` | Request a reset link |
| `POST` | `/api/v1/auth/password/reset/validate` | Check a link before showing the form |
| `POST` | `/api/v1/auth/password/reset` | Set the new password |

Request:

```json
{ "email": "admin@example.com" }
```

Response (`200`):

```json
{ "success": true, "data": { "sent": true } }
```

The response is identical for unknown, inactive and valid accounts. If the
email belongs to accounts in several organizations, each account gets its own
link.

A reset link contains a 256-bit random token. Only its SHA-256 hash is stored in
`password_reset_tokens`; the raw token is emailed and never logged. Links:

- expire after **30 minutes** (`app.security.password-reset.token-ttl-minutes`);
- are **single-use** and redeemed under a pessimistic row lock, so two
  concurrent submissions cannot both succeed;
- are invalidated when a newer reset is requested for the same account;
- are invalidated for all sessions once one is used.

On success the server:

1. validates the new password against `PasswordPolicy`;
2. rejects reuse of the current password;
3. updates the BCrypt hash and `last_password_change_at`;
4. increments `auth_version` (invalidating every existing access/refresh token);
5. revokes every refresh token;
6. marks the reset token used and invalidates any sibling tokens.

The user then signs in again with the new password. The UI redirects to
`/login?reset=1` and shows a success notice.

### Email delivery

Mail is **optional and disabled by default**. When it is disabled the endpoint
still behaves identically (no account-existence leak), it simply cannot deliver
links. Configure SMTP:

```dotenv
MAIL_ENABLED=true
EMAIL_PROVIDER=
SMTP_SERVER=smtp.your-domain.example
SMTP_PORT=587
SMTP_EMAIL=mailer@your-domain.example
SMTP_PASSWORD=…
SMTP_USE_SSL=false
MAIL_FROM=mailer@your-domain.example
APP_FRONTEND_URL=https://app.your-domain.example
```

`APP_FRONTEND_URL` is used to build the link; it must be the public web origin,
not the API origin. The reset email uses the shared responsive SmartOS email
layout, with an HTML version and a plain-text alternative. It has a light-theme
fallback and adapts its surfaces and text for dark-mode clients that support
theme media queries. New application emails should use the same shared template.

Use either `SMTP_SERVER` or a supported `EMAIL_PROVIDER` preset (`gmail`,
`outlook`, `office365`, `sendgrid`, `mailgun`, or `postmark`). A preset fills in
the SMTP host and port only when those values are blank. `SMTP_EMAIL` and
`SMTP_PASSWORD` are the credentials issued by the provider (Gmail commonly
requires an app password); `MAIL_FROM` should be an address verified with the
provider and defaults to `SMTP_EMAIL` when omitted. Port 587 uses required
STARTTLS; port 465 uses implicit TLS unless `SMTP_USE_SSL` explicitly overrides
it. Credentials, host, port, and TLS mode must match the provider's SMTP
instructions. Existing `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
`SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD` settings remain supported.

For example, with Gmail choose `EMAIL_PROVIDER=gmail`, leave `SMTP_SERVER`
blank, set `SMTP_PORT=587` and `SMTP_USE_SSL=false`, use the Gmail account as
`SMTP_EMAIL` and `MAIL_FROM`, and provide a Google app password as
`SMTP_PASSWORD` (Google account 2-Step Verification must be enabled). For a
different provider, use its SMTP endpoint and provider-issued SMTP credentials;
the host/port presets are `smtp-mail.outlook.com`, `smtp.office365.com`,
`smtp.sendgrid.net`, `smtp.mailgun.org`, and `smtp.postmarkapp.com`, all on port
587 by default. Never use an account's ordinary sign-in password unless the
provider specifically issues it for SMTP.

`MAIL_ENABLED=true` alone does not enable delivery. With missing SMTP
configuration, the endpoint continues to return its generic success response
to avoid disclosing whether an account exists, while the application logs a
secret-free configuration or delivery error. Check startup/application logs
and SMTP provider credentials when delivery fails. A failed delivery does not
leave a usable reset token active.

### Local development without SMTP

Set `PASSWORD_RESET_EXPOSE_DEV_TOKEN=true`. The forgot response then includes
`devToken`, and `/forgot-password` renders a direct link to
`/reset-password?token=…`. **Never enable this in production** — it defeats the
purpose of email verification.

### Enumeration and abuse protection

- Generic responses and constant-time account handling.
- Reset requests are limited to 4 per email + IP per 30 minutes by default.
- Tokens are 32 random bytes, hashed at rest, and expired/used tokens return a
  generic `400` ("invalid or has expired").
- Raw tokens are never written to logs.

---

## 4. Change password (Settings → Security)

`POST /api/v1/users/me/password` (authenticated)

```json
{
  "currentPassword": "…",
  "newPassword": "…",
  "confirmPassword": "…",
  "keepCurrentSession": true
}
```

- Verifies the current password before changing anything.
- Applies the same password policy and rejects reuse of the current password.
- Increments `auth_version`, revoking every access token and refresh token.
- Always revokes all refresh tokens. When `keepCurrentSession` is true it also
  returns a fresh token pair for this device; otherwise the client clears its
  tokens and redirects to login.
- The UI shows inline validation, a strength meter, success feedback, and a
  show/hide password control on every password field.

---

## 5. Passkeys (WebAuthn / FIDO2)

Passkeys are real server-verified WebAuthn credentials, not a client-side
biometric check. The browser signs a server challenge with a private key held by
the platform authenticator or password manager; the server verifies the
signature with the stored public key.

### Management (Settings → Security → Passkeys)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/users/me/passkeys` | List the caller's passkeys |
| `POST` | `/api/v1/users/me/passkeys/options` | Registration challenge + options |
| `POST` | `/api/v1/users/me/passkeys` | Verify attestation and store the credential |
| `PATCH` | `/api/v1/users/me/passkeys/{id}` | Rename |
| `DELETE` | `/api/v1/users/me/passkeys/{id}` | Delete |

- Every management endpoint is authenticated and scoped to the current user.
  A credential id belonging to another user is treated as not found.
- Maximum 20 passkeys per user.
- `excludeCredentials` prevents registering the same authenticator twice.

### Sign-in

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/passkeys/options` | Challenge (optionally scoped by email) |
| `POST` | `/api/v1/auth/passkeys/verify` | Verify assertion and issue tokens |

- With an email, `allowCredentials` is scoped to that user's credentials; without
  one, the browser may offer a discoverable credential.
- The login page offers “Sign in with a passkey” next to the password form and a
  password fallback is always available.
- If the account has TOTP enabled, a successful passkey assertion still returns
  a 2FA challenge — a passkey does not silently bypass an existing second factor.
- Users without a passkey can always use password or password reset.

### Server-side checks

`WebAuthnVerifier` validates for every ceremony:

- client-data `type` (`webauthn.create` / `webauthn.get`) and `origin` against
  `WEBAUTHN_ALLOWED_ORIGINS` (trailing slashes normalised);
- `crossOrigin` is not true;
- a non-empty challenge, looked up hashed and single-use in
  `webauthn_challenges`, scoped to the ceremony purpose and user;
- RP ID hash against `WEBAUTHN_RP_ID`;
- user-presence flag, and user-verification when required;
- attestation format (`none` or `packed`, including self-attestation and x5c);
- assertion signature with the stored COSE public key (ES256, RS256, EdDSA);
- the signature counter increases, detecting cloned authenticators;
- user-handle ownership for discoverable credentials.

Challenges are random 32-byte values, stored as SHA-256 hashes, and expire after
5 minutes (`app.webauthn.challenge-ttl-seconds`).

### Deployment requirements

WebAuthn only works in a secure context:

- Production must use **HTTPS**. `http://localhost` is the only accepted
  insecure origin (and only for development).
- `WEBAUTHN_RP_ID` must be the registrable domain, e.g. `app.example.com` or
  `example.com` (no scheme, no port). It must cover the web origin.
- `WEBAUTHN_ALLOWED_ORIGINS` is a comma-separated list of exact origins, e.g.
  `https://app.example.com`.
- The frontend and API may live on different hosts, but the web origin must be
  listed and CORS must allow it (`CORS_ORIGINS`).
- `WEBAUTHN_USER_VERIFICATION=preferred` (default) or `required`. Use `required`
  when you want to insist on biometric/PIN verification rather than presence.

Changing `WEBAUTHN_RP_ID` invalidates existing passkeys, because credentials are
bound to the RP ID.

---

## 6. Sessions and revocation

- Access tokens are stateless JWTs (15 minutes) carrying `sub`, `orgId`,
  `roles`, `email` and the user's `ver` (auth version).
- Refresh tokens are random, rotated on use, and stored hashed. Reuse of a
  rotated refresh token revokes the whole token family.
- `JwtAuthenticationFilter` compares the token's `ver` with the live
  `app_users.auth_version`. Changing the password, resetting it, or any other
  deliberate invalidation increments the version, so old access tokens stop
  working immediately.
- `last_password_change_at` is recorded for auditing and UI hints.

---

## 7. Configuration reference

| Variable | Default | Notes |
| --- | --- | --- |
| `JWT_SECRET` | dev placeholder | Must be 32+ random bytes; required in production |
| `CORS_ORIGINS` | `http://localhost:3000` | Comma-separated web origins |
| `APP_FRONTEND_URL` | `http://localhost:3000` | Used in reset links |
| `MAIL_ENABLED` | `false` | Enables reset email delivery |
| `EMAIL_PROVIDER` | empty | Optional host preset: `gmail`, `outlook`, `office365`, `sendgrid`, `mailgun`, or `postmark` |
| `SMTP_SERVER` | empty | SMTP server host; required unless a provider preset is selected |
| `SMTP_PORT` | provider default / `587` | SMTP port; 587 uses STARTTLS and 465 implicit TLS by default |
| `SMTP_EMAIL` | empty | SMTP login name and fallback sender address |
| `SMTP_PASSWORD` | empty | SMTP password or provider-issued SMTP credential |
| `SMTP_USE_SSL` | inferred from port | `true` for implicit TLS; `false` for STARTTLS |
| `MAIL_FROM` | `SMTP_EMAIL` or local fallback | Sender address; production address should be provider-verified |
| `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` | — | Legacy aliases, lower priority than corresponding `SMTP_*` values |
| `PASSWORD_RESET_EXPOSE_DEV_TOKEN` | `false` | Dev only; returns the token in the response |
| `TRUST_FORWARDED_HEADERS` | `false` | Only behind a trusted proxy that sets `X-Forwarded-For` |
| `WEBAUTHN_RP_ID` | `localhost` | Registrable domain of the web app |
| `WEBAUTHN_RP_NAME` | `SmartOS` | Shown by the authenticator |
| `WEBAUTHN_ALLOWED_ORIGINS` | `http://localhost:3000` | Exact allowed origins |
| `WEBAUTHN_USER_VERIFICATION` | `preferred` | `preferred` or `required` |

Rate-limit and token TTL values live in `application.yml` under
`app.security.*` and `app.webauthn.*`.

---

## 8. Database migration and deployment

Migration **`V12__auth_security.sql`** adds:

- `app_users.auth_version` (default `0`, not null) and
  `app_users.last_password_change_at`;
- `password_reset_tokens` (hashed single-use tokens, expiry, used marker);
- `passkey_credentials` (COSE public key, sign counter, transports, nickname);
- `webauthn_challenges` (hashed single-use challenges with purpose and expiry).

Flyway runs it automatically on startup; `ddl-auto: validate` will fail fast if
the schema is missing.

Deployment checklist:

1. Back up the database (standard practice before a schema change).
2. Deploy the backend and let Flyway apply `V12`.
3. Set `APP_FRONTEND_URL`, `CORS_ORIGINS`, and (for production)
   `WEBAUTHN_RP_ID` / `WEBAUTHN_ALLOWED_ORIGINS` to the real HTTPS origin.
4. Configure SMTP and set `MAIL_ENABLED=true`, or intentionally leave mail
   disabled (reset requests will be accepted but no email is sent).
5. Keep `PASSWORD_RESET_EXPOSE_DEV_TOKEN=false` and
   `TRUST_FORWARDED_HEADERS=false` unless the specific dev/proxy condition
   applies.
6. Serve the web app over HTTPS. Keep `/api/*` out of the service-worker cache.
7. Restart the old backend process; confirm the new one owns the port.
8. Smoke-test: login, forgot → reset → login, change password (other sessions
   signed out), register/use/delete a passkey, and 2FA fallback.

### Multi-instance rate limiting

`AuthRateLimiter` is in-process and intentionally simple. With more than one API
instance, put a shared limiter (e.g. Redis) in front of `/auth/login`,
`/auth/password/forgot` and `/auth/passkeys/verify`, or accept per-instance
limits. Do not enable `TRUST_FORWARDED_HEADERS` unless a trusted proxy
overwrites `X-Forwarded-For`.

---

## 9. Tests

Backend (`smartos-backend`):

```powershell
.\mvnw.cmd clean test
```

Relevant coverage:

- `PasswordPolicyTest` — length, common values, sequences, email reuse,
  letter/number rules.
- `PasswordResetServiceTest` — hashed tokens, TTL, unknown accounts, rate
  limiting, expired/reused links, single-use redemption, session revocation,
  auth-version bump, inactive accounts.
- `CborDecoderTest` — CBOR subset used by WebAuthn, truncation/indefinite
  rejection.
- `WebAuthnVerifierTest` — registration/assertion signatures, origin, RP ID,
  flags, counter replay, ceremony type, challenge presence.
- `AuthServiceTest`, `JwtSecurityHttpTest` — login resolution, refresh rotation,
  token-version enforcement.

Frontend (`smartos`):

```powershell
npx tsc --noEmit
npm test
npm run build
```

Relevant coverage: expired-token handling, password rules, WebAuthn base64url
and option conversion, forgot/reset API calls, password-change token rotation,
passkey CRUD, and the password show/hide control.

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `401` on `/auth/password/forgot` | Old backend process, or a stale Bearer token | Restart the backend; clear tokens; ensure public paths omit Authorization |
| Forgot returns `200` but no email | Mail disabled or SMTP misconfigured | Set `MAIL_ENABLED=true` and configure `SMTP_SERVER` (or a supported `EMAIL_PROVIDER`) plus valid provider credentials; use `PASSWORD_RESET_EXPOSE_DEV_TOKEN=true` only for local testing |
| Reset link says invalid | Expired (30 min), already used, or superseded by a newer request | Request a new link |
| Passkey button missing | Browser/device doesn't support WebAuthn, or insecure origin | Use HTTPS or `localhost`; try a platform authenticator or password manager |
| Passkey registration fails verification | Wrong RP ID/origin, or stale challenge | Match `WEBAUTHN_RP_ID`/`WEBAUTHN_ALLOWED_ORIGINS` to the page origin; retry |
| Passkey sign-in asks for 2FA | Account has TOTP enabled | Complete the authenticator code; this is intentional |
| Change password leaves the user signed out | `keepCurrentSession` was false | Expected; sign in with the new password |
| Login works in one tab but not another | Another tab holds an outdated token | Reload; the client refreshes once and clears expired tokens |
