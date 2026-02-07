-- SQL Server equivalent of PostgreSQL 016_widen_alternate_id_type_column.sql
-- Widen id_type column from VARCHAR(20) to VARCHAR(50) to support free-form alternate ID types.
-- In SQL Server, ALTER COLUMN must re-specify nullability.
ALTER TABLE dbo.contact_alternate_ids ALTER COLUMN id_type VARCHAR(50) NOT NULL;
