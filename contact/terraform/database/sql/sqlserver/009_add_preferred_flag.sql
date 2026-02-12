-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 009_add_preferred_flag.sql
-- Add preferred flag to contact_addresses and contact_emails tables

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contact_addresses') AND name = 'preferred')
    ALTER TABLE dbo.contact_addresses ADD preferred BIT NOT NULL DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contact_emails') AND name = 'preferred')
    ALTER TABLE dbo.contact_emails ADD preferred BIT NOT NULL DEFAULT 0;
