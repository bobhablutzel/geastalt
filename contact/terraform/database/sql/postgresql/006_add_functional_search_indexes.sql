-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create functional indexes for case-insensitive name search
-- These indexes use LOWER() with varchar_pattern_ops for efficient prefix queries (e.g., 'smith%')

CREATE INDEX IF NOT EXISTS idx_contacts_last_name_lower ON public.contacts (LOWER(last_name) varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_contacts_first_name_lower ON public.contacts (LOWER(first_name) varchar_pattern_ops);

COMMENT ON INDEX idx_contacts_last_name_lower IS 'Functional index for case-insensitive last name prefix searches';
COMMENT ON INDEX idx_contacts_first_name_lower IS 'Functional index for case-insensitive first name prefix searches';
