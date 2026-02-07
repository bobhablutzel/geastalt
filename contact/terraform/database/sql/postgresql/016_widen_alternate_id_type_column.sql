-- Widen id_type column from VARCHAR(20) to VARCHAR(50) to support free-form alternate ID types.
-- This is a metadata-only change in PostgreSQL (no table rewrite) because we are only increasing the limit.
ALTER TABLE contact_alternate_ids ALTER COLUMN id_type TYPE VARCHAR(50);
