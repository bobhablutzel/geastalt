-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Add lowercase search columns to contacts table
-- This avoids functional index overhead by storing pre-computed lowercase values

-- Add the search columns
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS last_name_lower VARCHAR(255);
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS first_name_lower VARCHAR(255);

-- Populate existing data
UPDATE public.contacts SET
    last_name_lower = LOWER(last_name),
    first_name_lower = LOWER(first_name);

-- Create indexes with varchar_pattern_ops for prefix searches
CREATE INDEX IF NOT EXISTS idx_contacts_last_name_search ON public.contacts (last_name_lower varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_contacts_first_name_search ON public.contacts (first_name_lower varchar_pattern_ops);

-- Create trigger function to keep search columns in sync
CREATE OR REPLACE FUNCTION sync_contact_search_columns()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_name_lower := LOWER(NEW.last_name);
    NEW.first_name_lower := LOWER(NEW.first_name);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for INSERT and UPDATE
DROP TRIGGER IF EXISTS trg_sync_contact_search ON public.contacts;
CREATE TRIGGER trg_sync_contact_search
    BEFORE INSERT OR UPDATE ON public.contacts
    FOR EACH ROW
    EXECUTE FUNCTION sync_contact_search_columns();

-- Drop the old functional indexes (no longer needed)
DROP INDEX IF EXISTS idx_contacts_last_name_lower;
DROP INDEX IF EXISTS idx_contacts_first_name_lower;

COMMENT ON COLUMN public.contacts.last_name_lower IS 'Pre-computed lowercase last_name for efficient case-insensitive search';
COMMENT ON COLUMN public.contacts.first_name_lower IS 'Pre-computed lowercase first_name for efficient case-insensitive search';
