-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Add preferred flag to contact_addresses and contact_emails tables

ALTER TABLE public.contact_addresses ADD COLUMN IF NOT EXISTS preferred BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.contact_emails ADD COLUMN IF NOT EXISTS preferred BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN public.contact_addresses.preferred IS 'Indicates if this is the preferred address for the contact';
COMMENT ON COLUMN public.contact_emails.preferred IS 'Indicates if this is the preferred email for the contact';
