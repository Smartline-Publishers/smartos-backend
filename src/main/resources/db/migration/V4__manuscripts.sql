-- V4: manuscripts + editorial workflow
CREATE TABLE IF NOT EXISTS manuscripts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  book_id UUID REFERENCES books(id) ON DELETE SET NULL,
  contributor_id UUID REFERENCES contributors(id) ON DELETE SET NULL,
  title VARCHAR(300) NOT NULL,
  stage VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  assignee_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  due_date DATE,
  version INT NOT NULL DEFAULT 1,
  file_url VARCHAR(1024),
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_manuscripts_org ON manuscripts(organization_id);
CREATE INDEX IF NOT EXISTS idx_manuscripts_stage ON manuscripts(stage);
CREATE INDEX IF NOT EXISTS idx_manuscripts_title ON manuscripts(title);
CREATE INDEX IF NOT EXISTS idx_manuscripts_assignee ON manuscripts(assignee_id);

CREATE TABLE IF NOT EXISTS manuscript_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  manuscript_id UUID NOT NULL REFERENCES manuscripts(id) ON DELETE CASCADE,
  from_stage VARCHAR(32),
  to_stage VARCHAR(32) NOT NULL,
  actor_id UUID,
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_manuscript_events_manuscript ON manuscript_events(manuscript_id);
