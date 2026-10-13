-- V8: finance (immutable transactions, budgets, contracts, royalty statements)
-- Transactions are insert-only: corrections are new rows (e.g. REFUND), never edits.
CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  type VARCHAR(16) NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  category VARCHAR(120),
  reference_type VARCHAR(32),
  reference_id UUID,
  description VARCHAR(500),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_org ON transactions(organization_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category);
CREATE INDEX IF NOT EXISTS idx_transactions_reference ON transactions(reference_type, reference_id);

CREATE TABLE IF NOT EXISTS budgets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(120),
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  planned_amount NUMERIC(12,2) NOT NULL,
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_budgets_org ON budgets(organization_id);

CREATE TABLE IF NOT EXISTS contracts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  contributor_id UUID NOT NULL REFERENCES contributors(id),
  book_id UUID REFERENCES books(id),
  royalty_rate NUMERIC(7,4) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contracts_org ON contracts(organization_id);
CREATE INDEX IF NOT EXISTS idx_contracts_contributor ON contracts(contributor_id);

CREATE TABLE IF NOT EXISTS royalty_statements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  contract_id UUID NOT NULL REFERENCES contracts(id),
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  net_sales NUMERIC(12,2) NOT NULL,
  rate_snapshot NUMERIC(7,4) NOT NULL,
  payable NUMERIC(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  approved_by UUID,
  approved_at TIMESTAMPTZ,
  paid_at TIMESTAMPTZ,
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_royalty_statements_org ON royalty_statements(organization_id);
CREATE INDEX IF NOT EXISTS idx_royalty_statements_contract ON royalty_statements(contract_id);
CREATE INDEX IF NOT EXISTS idx_royalty_statements_status ON royalty_statements(status);
