-- V11: organization branding/theme, user profile + 2FA, onboarding progress
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS theme JSONB;

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(128);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 2FA backup codes: only SHA-256 hashes are persisted, single-use.
CREATE TABLE IF NOT EXISTS user_backup_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  code_hash VARCHAR(128) NOT NULL UNIQUE,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_backup_codes_user ON user_backup_codes(user_id);

-- Onboarding checklist persistence (per administrator, hence per organization:
-- each user belongs to exactly one organization).
CREATE TABLE IF NOT EXISTS onboarding_progress (
  user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  theme_skipped BOOLEAN NOT NULL DEFAULT FALSE,
  dismissed BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_progress_org ON onboarding_progress(organization_id);
