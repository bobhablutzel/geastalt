-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 013_create_contracts_table.sql
-- Create contracts table for contract definitions
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contracts' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contracts (
        id              UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
        contract_name   VARCHAR(255) NOT NULL,
        company_id      UNIQUEIDENTIFIER NOT NULL,
        company_name    VARCHAR(255) NOT NULL
    );
END;

-- Create index for company lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contracts_company_id' AND object_id = OBJECT_ID('dbo.contracts'))
    CREATE INDEX idx_contracts_company_id ON dbo.contracts(company_id);
