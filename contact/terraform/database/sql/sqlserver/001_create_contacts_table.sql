-- SQL Server equivalent of PostgreSQL 001_create_contacts_table.sql
-- Create contacts table
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contacts' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contacts (
        id         BIGINT IDENTITY(1,1) PRIMARY KEY,
        email      VARCHAR(255),
        first_name VARCHAR(255),
        last_name  VARCHAR(255),
        created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
    );
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_email' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_email ON dbo.contacts(email);
