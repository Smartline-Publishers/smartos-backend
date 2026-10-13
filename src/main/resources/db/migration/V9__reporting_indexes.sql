-- V9: reporting indexes (window queries over created/updated timestamps)
CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_updated ON orders(updated_at);
CREATE INDEX IF NOT EXISTS idx_manuscript_events_created ON manuscript_events(created_at);
CREATE INDEX IF NOT EXISTS idx_statements_created ON royalty_statements(created_at);
CREATE INDEX IF NOT EXISTS idx_books_created ON books(created_at);
