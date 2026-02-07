-- Create contact_alternate_ids table for multiple alternate ID types per contact
-- Migrates existing external_id data from contacts table

-- Create the new table
CREATE TABLE IF NOT EXISTS public.contact_alternate_ids (
    id BIGSERIAL PRIMARY KEY,
    contact_id BIGINT NOT NULL REFERENCES public.contacts(id),
    id_type VARCHAR(20) NOT NULL,  -- NEW_NATIONS, OLD_NATIONS, PAN_HASH, CONTACT_TUPLE
    alternate_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_contact_alternate_ids_contact_type UNIQUE (contact_id, id_type),
    CONSTRAINT uk_contact_alternate_ids_type_alternate UNIQUE (id_type, alternate_id)
);

-- Create indexes for efficient lookups
CREATE INDEX IF NOT EXISTS idx_contact_alternate_ids_contact ON public.contact_alternate_ids(contact_id);
CREATE INDEX IF NOT EXISTS idx_contact_alternate_ids_lookup ON public.contact_alternate_ids(id_type, alternate_id);

-- Add comments
COMMENT ON TABLE public.contact_alternate_ids IS 'Alternate identifiers for contacts, supporting multiple ID types per contact';
COMMENT ON COLUMN public.contact_alternate_ids.id_type IS 'Type of alternate ID: NEW_NATIONS, OLD_NATIONS, PAN_HASH, CONTACT_TUPLE';
COMMENT ON COLUMN public.contact_alternate_ids.alternate_id IS 'The alternate identifier value';

-- Migrate existing external_id data from contacts table as NEW_NATIONS type
INSERT INTO public.contact_alternate_ids (contact_id, id_type, alternate_id, created_at)
SELECT id, 'NEW_NATIONS', external_id, NOW()
FROM public.contacts
WHERE external_id IS NOT NULL
ON CONFLICT (contact_id, id_type) DO NOTHING;
