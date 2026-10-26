-- V13: versioned legal documents + acceptance evidence
--
-- Legal text is stored as an immutable, versioned document. A single version
-- per code may be current at a time (enforced by a partial unique index), and
-- every acceptance points at the exact code/version the user agreed to. The
-- content hash lets an auditor detect if a published version was ever altered.

CREATE TABLE IF NOT EXISTS legal_documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(32) NOT NULL,
  version VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary TEXT,
  effective_date DATE NOT NULL,
  content JSONB NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_legal_documents_code_version UNIQUE (code, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_legal_documents_current_code
  ON legal_documents(code) WHERE is_current;

CREATE TABLE IF NOT EXISTS legal_acceptances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  document_id UUID NOT NULL REFERENCES legal_documents(id),
  document_code VARCHAR(32) NOT NULL,
  document_version VARCHAR(32) NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  context VARCHAR(32) NOT NULL,
  invite_id UUID REFERENCES invites(id) ON DELETE SET NULL,
  ip_address VARCHAR(64),
  user_agent VARCHAR(512),
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_legal_acceptances_user
  ON legal_acceptances(user_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS idx_legal_acceptances_org
  ON legal_acceptances(organization_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS idx_legal_acceptances_document
  ON legal_acceptances(document_code, document_version);
