-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 014_create_contact_contracts_table.sql
-- Create contact_contracts table linking contacts to their contracts
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_contracts' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_contracts (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id      BIGINT NOT NULL REFERENCES dbo.contacts(id),
        contract_id     UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.contracts(id),
        effective_date  DATETIMEOFFSET NOT NULL,
        expiration_date DATETIMEOFFSET NOT NULL,
        created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        updated_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
    );
END;

-- Create indexes for faster lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_contracts_contact_id' AND object_id = OBJECT_ID('dbo.contact_contracts'))
    CREATE INDEX idx_contact_contracts_contact_id ON dbo.contact_contracts(contact_id);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_contracts_contract_id' AND object_id = OBJECT_ID('dbo.contact_contracts'))
    CREATE INDEX idx_contact_contracts_contract_id ON dbo.contact_contracts(contract_id);
