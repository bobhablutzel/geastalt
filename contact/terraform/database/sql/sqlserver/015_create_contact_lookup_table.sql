-- SQL Server equivalent of PostgreSQL 015_create_contact_lookup_table.sql
-- Create contact_lookup table for partition-aware contact lookups
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_lookup' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_lookup (
        contact_id       BIGINT PRIMARY KEY REFERENCES dbo.contacts(id),
        partition_number INTEGER NOT NULL,
        created_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
    );
END;

-- Create index for partition-based queries
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_lookup_partition' AND object_id = OBJECT_ID('dbo.contact_lookup'))
    CREATE INDEX idx_contact_lookup_partition ON dbo.contact_lookup(partition_number);
