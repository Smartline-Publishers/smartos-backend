-- V15: link organizations to Clerk organizations (stable external id)
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clerk_org_id VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizations_clerk_org_id ON organizations(clerk_org_id) WHERE clerk_org_id IS NOT NULL;