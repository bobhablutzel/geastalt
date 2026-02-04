-- Add lowercase search columns to members table
-- This avoids functional index overhead by storing pre-computed lowercase values

-- Add the search columns
ALTER TABLE members ADD COLUMN IF NOT EXISTS last_name_lower VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS first_name_lower VARCHAR(255);

-- Populate existing data
UPDATE members SET
    last_name_lower = LOWER(last_name),
    first_name_lower = LOWER(first_name);

-- Create indexes with varchar_pattern_ops for prefix searches
CREATE INDEX IF NOT EXISTS idx_members_last_name_search ON members (last_name_lower varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_members_first_name_search ON members (first_name_lower varchar_pattern_ops);

-- Create trigger function to keep search columns in sync
CREATE OR REPLACE FUNCTION sync_member_search_columns()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_name_lower := LOWER(NEW.last_name);
    NEW.first_name_lower := LOWER(NEW.first_name);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for INSERT and UPDATE
DROP TRIGGER IF EXISTS trg_sync_member_search ON members;
CREATE TRIGGER trg_sync_member_search
    BEFORE INSERT OR UPDATE ON members
    FOR EACH ROW
    EXECUTE FUNCTION sync_member_search_columns();

-- Drop the old functional indexes (no longer needed)
DROP INDEX IF EXISTS idx_members_last_name_lower;
DROP INDEX IF EXISTS idx_members_first_name_lower;

COMMENT ON COLUMN members.last_name_lower IS 'Pre-computed lowercase last_name for efficient case-insensitive search';
COMMENT ON COLUMN members.first_name_lower IS 'Pre-computed lowercase first_name for efficient case-insensitive search';
