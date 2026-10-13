-- V10: SaaS self-serve (invites, refresh-token rotation store, billing stub)
CREATE TABLE IF NOT EXISTS invites (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  email VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invites_org ON invites(organization_id);
CREATE INDEX IF NOT EXISTS idx_invites_email ON invites(email);

-- Refresh-token rotation store: only hashes are persisted, never raw tokens.
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  replaced_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Billing stub: one subscription row per org, driven by webhook events.
CREATE TABLE IF NOT EXISTS subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
  provider VARCHAR(32) NOT NULL DEFAULT 'STRIPE_STUB',
  external_id VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'TRIALING',
  current_period_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Webhook idempotency keys: provider event ids, processed at most once.
CREATE TABLE IF NOT EXISTS billing_events (
  provider_event_id VARCHAR(255) PRIMARY KEY,
  type VARCHAR(128) NOT NULL,
  payload JSONB,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
