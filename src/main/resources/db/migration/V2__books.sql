-- V2: books domain
CREATE TABLE IF NOT EXISTS books (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  title VARCHAR(500) NOT NULL,
  subtitle VARCHAR(500),
  isbn VARCHAR(32),
  imprint VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  language VARCHAR(16),
  page_count INT,
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_books_org_isbn UNIQUE (organization_id, isbn)
);

CREATE INDEX IF NOT EXISTS idx_books_org ON books(organization_id);
CREATE INDEX IF NOT EXISTS idx_books_status ON books(status);
CREATE INDEX IF NOT EXISTS idx_books_title ON books(title);

CREATE TABLE IF NOT EXISTS contributors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  full_name VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL DEFAULT 'AUTHOR',
  email VARCHAR(255),
  bio TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contributors_org ON contributors(organization_id);

CREATE TABLE IF NOT EXISTS book_contributors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  contributor_id UUID NOT NULL REFERENCES contributors(id) ON DELETE CASCADE,
  role VARCHAR(32) NOT NULL DEFAULT 'AUTHOR',
  royalty_pct DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_book_contributors_book ON book_contributors(book_id);
CREATE INDEX IF NOT EXISTS idx_book_contributors_contributor ON book_contributors(contributor_id);
