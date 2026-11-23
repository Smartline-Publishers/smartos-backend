-- V14: link app_users to Clerk accounts (stable external id) and allow
-- users without an organization yet (Clerk users are provisioned before
-- they create their first organization).
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS clerk_user_id VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_clerk_user_id ON app_users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;
ALTER TABLE app_users ALTER COLUMN organization_id DROP NOT NULL;