-- Create contact_lookup table for partition-aware contact lookups
-- This script is idempotent and can be run multiple times safely

CREATE TABLE IF NOT EXISTS public.contact_lookup (
    contact_id        BIGINT PRIMARY KEY REFERENCES public.contacts(id),
    partition_number INTEGER NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for partition-based queries
CREATE INDEX IF NOT EXISTS idx_contact_lookup_partition ON public.contact_lookup(partition_number);

-- Add comment for documentation
COMMENT ON TABLE public.contact_lookup IS 'Partition-aware contact lookup table, referenced by contact_alternate_ids';
