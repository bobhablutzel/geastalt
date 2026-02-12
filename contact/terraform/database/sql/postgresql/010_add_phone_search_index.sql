-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Migration 010: Add index for phone number searches
-- This enables efficient lookups of contacts by phone number

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_contact_phones_phone_number
ON public.contact_phones (phone_number);
