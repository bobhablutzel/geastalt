-- Create functional indexes for case-insensitive name search
-- These indexes use LOWER() with varchar_pattern_ops for efficient prefix queries (e.g., 'smith%')

CREATE INDEX IF NOT EXISTS idx_members_last_name_lower ON members (LOWER(last_name) varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_members_first_name_lower ON members (LOWER(first_name) varchar_pattern_ops);

COMMENT ON INDEX idx_members_last_name_lower IS 'Functional index for case-insensitive last name prefix searches';
COMMENT ON INDEX idx_members_first_name_lower IS 'Functional index for case-insensitive first name prefix searches';
