-- Add external_id column to members table for non-sequential member identifiers
-- External IDs have format "NH" + 15 digits (e.g., "NH847293615028374")

-- Add the external_id column
ALTER TABLE members ADD COLUMN IF NOT EXISTS external_id VARCHAR(17);

-- Create unique index for external_id lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_members_external_id ON members (external_id);

COMMENT ON COLUMN members.external_id IS 'Non-sequential external identifier in format NH + 15 digits';
