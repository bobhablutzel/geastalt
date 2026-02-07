-- SQL Server equivalent of PostgreSQL 004_create_contact_emails_table.sql
-- Create contact_emails table
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_emails' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_emails (
        id         BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id BIGINT NOT NULL REFERENCES dbo.contacts(id),
        email      VARCHAR(255) NOT NULL,
        email_type VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_email_type UNIQUE (contact_id, email_type)
    );
END;

-- Create index for faster lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_emails_contact_id' AND object_id = OBJECT_ID('dbo.contact_emails'))
    CREATE INDEX idx_contact_emails_contact_id ON dbo.contact_emails(contact_id);
