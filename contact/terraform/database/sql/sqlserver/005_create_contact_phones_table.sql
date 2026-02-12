-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 005_create_contact_phones_table.sql
-- Create contact_phones table
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_phones' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_phones (
        id           BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id   BIGINT NOT NULL REFERENCES dbo.contacts(id),
        phone_number VARCHAR(50) NOT NULL,
        phone_type   VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_phone_type UNIQUE (contact_id, phone_type)
    );
END;

-- Create index for faster lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_phones_contact_id' AND object_id = OBJECT_ID('dbo.contact_phones'))
    CREATE INDEX idx_contact_phones_contact_id ON dbo.contact_phones(contact_id);
