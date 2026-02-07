-- SQL Server equivalent of PostgreSQL 008_add_external_id_column.sql
-- Add external_id column to contacts table for non-sequential contact identifiers
-- External IDs have format "NH" + 15 digits (e.g., "NH847293615028374")

-- Add the external_id column
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contacts') AND name = 'external_id')
    ALTER TABLE dbo.contacts ADD external_id VARCHAR(17);

-- Create unique index for external_id lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_external_id' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE UNIQUE INDEX idx_contacts_external_id ON dbo.contacts(external_id) WHERE external_id IS NOT NULL;
