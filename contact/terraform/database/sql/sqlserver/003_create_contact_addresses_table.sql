-- SQL Server equivalent of PostgreSQL 003_create_contact_addresses_table.sql
-- Create contact_addresses table
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_addresses' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_addresses (
        id           BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id   BIGINT NOT NULL REFERENCES dbo.contacts(id),
        address_id   BIGINT NOT NULL REFERENCES dbo.standardized_addresses(id),
        address_type VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_address_type UNIQUE (contact_id, address_type)
    );
END;

-- Create indexes for faster lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_addresses_contact_id' AND object_id = OBJECT_ID('dbo.contact_addresses'))
    CREATE INDEX idx_contact_addresses_contact_id ON dbo.contact_addresses(contact_id);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_addresses_address_id' AND object_id = OBJECT_ID('dbo.contact_addresses'))
    CREATE INDEX idx_contact_addresses_address_id ON dbo.contact_addresses(address_id);
