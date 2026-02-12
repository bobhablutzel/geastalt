-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_pending_actions table for tracking pending actions per contact

CREATE TABLE IF NOT EXISTS public.contact_pending_actions (
    id BIGSERIAL PRIMARY KEY,
    contact_id BIGINT NOT NULL REFERENCES public.contacts(id),
    action_type VARCHAR(50) NOT NULL,  -- GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_contact_pending_actions_contact_type UNIQUE (contact_id, action_type)
);

-- Create index for efficient lookups by contact
CREATE INDEX IF NOT EXISTS idx_contact_pending_actions_contact ON public.contact_pending_actions(contact_id);

-- Create index for finding all contacts with a specific pending action
CREATE INDEX IF NOT EXISTS idx_contact_pending_actions_type ON public.contact_pending_actions(action_type);

-- Add comments
COMMENT ON TABLE public.contact_pending_actions IS 'Pending actions for contacts, supporting zero to many actions per contact';
COMMENT ON COLUMN public.contact_pending_actions.action_type IS 'Type of pending action: GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS';
