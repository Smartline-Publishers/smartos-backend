-- V12: hardened authentication (password reset, session invalidation, passkeys)
--
-- auth_version is embedded in access/refresh tokens. Bumping it invalidates
-- every token minted before a password change/reset without needing to scan
-- the refresh-token table on every request.
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS auth_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS last_password_change_at TIMESTAMPTZ;

-- Password reset: only a SHA-256 hash of the single-use token is stored.
-- The raw token exists only in the email link.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  requested_ip VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expiry ON password_reset_tokens(expires_at);

-- WebAuthn/FIDO2 credentials. Only the public key (COSE) is persisted; no
-- biometric data ever leaves the authenticator.
CREATE TABLE IF NOT EXISTS passkey_credentials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  credential_id VARCHAR(1024) NOT NULL UNIQUE,
  public_key_cose BYTEA NOT NULL,
  sign_count BIGINT NOT NULL DEFAULT 0,
  transports VARCHAR(255),
  aaguid VARCHAR(64),
  nickname VARCHAR(100) NOT NULL,
  backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  backup_state BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_passkey_credentials_user ON passkey_credentials(user_id);

-- Short-lived WebAuthn challenges. They are single-use and consumed with a row
-- lock so a replayed assertion/attestation cannot succeed twice.
CREATE TABLE IF NOT EXISTS webauthn_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
  challenge_hash VARCHAR(128) NOT NULL UNIQUE,
  purpose VARCHAR(32) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_webauthn_challenges_expiry ON webauthn_challenges(expires_at);
