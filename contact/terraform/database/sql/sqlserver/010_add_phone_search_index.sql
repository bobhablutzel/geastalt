-- SQL Server equivalent of PostgreSQL 010_add_phone_search_index.sql
-- Migration 010: Add index for phone number searches
-- This enables efficient lookups of contacts by phone number
-- Uses WITH (ONLINE = ON) as the SQL Server equivalent of CONCURRENTLY

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_phones_phone_number' AND object_id = OBJECT_ID('dbo.contact_phones'))
    CREATE INDEX idx_contact_phones_phone_number ON dbo.contact_phones(phone_number) WITH (ONLINE = ON);
