-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server equivalent of PostgreSQL 016_widen_alternate_id_type_column.sql
-- Widen id_type column from VARCHAR(20) to VARCHAR(50) to support free-form alternate ID types.
-- In SQL Server, ALTER COLUMN must re-specify nullability.
ALTER TABLE dbo.contact_alternate_ids ALTER COLUMN id_type VARCHAR(50) NOT NULL;
