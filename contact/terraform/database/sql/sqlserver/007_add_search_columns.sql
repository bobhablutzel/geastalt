-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 007_add_search_columns.sql
-- Add lowercase search columns to contacts table
-- This avoids functional index overhead by storing pre-computed lowercase values

-- Add the search columns
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contacts') AND name = 'last_name_lower')
    ALTER TABLE dbo.contacts ADD last_name_lower VARCHAR(255);

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contacts') AND name = 'first_name_lower')
    ALTER TABLE dbo.contacts ADD first_name_lower VARCHAR(255);
GO

-- Populate existing data
UPDATE dbo.contacts SET
    last_name_lower = LOWER(last_name),
    first_name_lower = LOWER(first_name);
GO

-- Create indexes for prefix searches
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_last_name_search' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_last_name_search ON dbo.contacts(last_name_lower);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_first_name_search' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_first_name_search ON dbo.contacts(first_name_lower);
GO

-- Create trigger to keep search columns in sync
-- SQL Server uses AFTER triggers with the inserted pseudo-table
CREATE OR ALTER TRIGGER trg_sync_contact_search
ON dbo.contacts
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE c
    SET c.last_name_lower = LOWER(i.last_name),
        c.first_name_lower = LOWER(i.first_name)
    FROM dbo.contacts c
    INNER JOIN inserted i ON c.id = i.id;
END;
GO

-- Drop the old functional indexes (no longer needed, superseded by search column indexes)
IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_last_name_lower' AND object_id = OBJECT_ID('dbo.contacts'))
    DROP INDEX idx_contacts_last_name_lower ON dbo.contacts;

IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_first_name_lower' AND object_id = OBJECT_ID('dbo.contacts'))
    DROP INDEX idx_contacts_first_name_lower ON dbo.contacts;
