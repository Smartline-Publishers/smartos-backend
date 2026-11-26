-- V16: performance indexes for frequently filtered/joined/sorted columns.
-- All statements are idempotent and do not touch existing data.

-- app_users: role/active lookups within an organization.
CREATE INDEX IF NOT EXISTS idx_app_users_org_role ON app_users(organization_id, role);
CREATE INDEX IF NOT EXISTS idx_app_users_org_active ON app_users(organization_id, active);

-- organizations: Clerk linkage.
CREATE INDEX IF NOT EXISTS idx_organizations_clerk_org_id ON organizations(clerk_org_id);

-- books: org + status / created_at for list + dashboard.
CREATE INDEX IF NOT EXISTS idx_books_org_status ON books(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_books_org_created ON books(organization_id, created_at DESC);

-- contributors: org + name for search/sort.
CREATE INDEX IF NOT EXISTS idx_contributors_org_name ON contributors(organization_id, full_name);

-- manuscripts: org + stage / created_at.
CREATE INDEX IF NOT EXISTS idx_manuscripts_org_stage ON manuscripts(organization_id, stage);
CREATE INDEX IF NOT EXISTS idx_manuscripts_org_created ON manuscripts(organization_id, created_at DESC);

-- tasks: org + status / assignee / due_date.
CREATE INDEX IF NOT EXISTS idx_tasks_org_status ON tasks(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_org_assignee ON tasks(organization_id, assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_org_due ON tasks(organization_id, due_date) WHERE due_date IS NOT NULL;

-- production jobs.
CREATE INDEX IF NOT EXISTS idx_production_jobs_org_status ON production_jobs(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_production_jobs_org_book ON production_jobs(organization_id, book_id);

-- orders: org + status / created_at.
CREATE INDEX IF NOT EXISTS idx_orders_org_status ON orders(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_org_created ON orders(organization_id, created_at DESC);

-- customers: org + name.
CREATE INDEX IF NOT EXISTS idx_customers_org_name ON customers(organization_id, name);

-- transactions: org + created_at / type.
CREATE INDEX IF NOT EXISTS idx_transactions_org_created ON transactions(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_org_type ON transactions(organization_id, type);

-- budgets: org + period.
CREATE INDEX IF NOT EXISTS idx_budgets_org_period ON budgets(organization_id, period_start, period_end);

-- contracts: org + status.
CREATE INDEX IF NOT EXISTS idx_contracts_org_status ON contracts(organization_id, status);

-- royalty statements: org + status / created_at.
CREATE INDEX IF NOT EXISTS idx_royalty_statements_org_status ON royalty_statements(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_royalty_statements_org_created ON royalty_statements(organization_id, created_at DESC);

-- stock movements: composite for level aggregation.
CREATE INDEX IF NOT EXISTS idx_stock_movements_org_book_wh ON stock_movements(organization_id, book_id, warehouse_id);

-- audit logs: org + created_at / actor.
CREATE INDEX IF NOT EXISTS idx_audit_org_created ON audit_logs(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_org_actor_created ON audit_logs(organization_id, actor_id, created_at DESC);

-- invites: org + email.
CREATE INDEX IF NOT EXISTS idx_invites_org_email ON invites(organization_id, email);

-- refresh tokens: user + revoked + expiry.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked ON refresh_tokens(user_id, revoked, expires_at);

-- Optional trigram indexes for case-insensitive substring search. Created only
-- when the pg_trgm extension is available; never fail the migration otherwise.
DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
    CREATE INDEX IF NOT EXISTS idx_books_title_trgm ON books USING gin (upper(title) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_manuscripts_title_trgm ON manuscripts USING gin (upper(title) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_tasks_title_trgm ON tasks USING gin (upper(title) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_customers_name_trgm ON customers USING gin (upper(name) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_contributors_name_trgm ON contributors USING gin (upper(full_name) gin_trgm_ops);
  END IF;
END $$;