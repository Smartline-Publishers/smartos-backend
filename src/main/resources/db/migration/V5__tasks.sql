-- V5: staff tasks (editorial, production, and general assignments)
CREATE TABLE IF NOT EXISTS tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  title VARCHAR(300) NOT NULL,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  assignee_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  book_id UUID REFERENCES books(id) ON DELETE SET NULL,
  manuscript_id UUID REFERENCES manuscripts(id) ON DELETE SET NULL,
  due_date DATE,
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tasks_org ON tasks(organization_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_book ON tasks(book_id);
CREATE INDEX IF NOT EXISTS idx_tasks_manuscript ON tasks(manuscript_id);
