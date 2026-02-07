-- Add external_id column to contacts table for non-sequential contact identifiers
-- External IDs have format "NH" + 15 digits (e.g., "NH847293615028374")

-- Add the external_id column
ALTER TABLE public.contacts ADD COLUMN IF NOT EXISTS external_id VARCHAR(17);

-- Create unique index for external_id lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_contacts_external_id ON public.contacts (external_id);

COMMENT ON COLUMN public.contacts.external_id IS 'Non-sequential external identifier in format NH + 15 digits';
