-- SQL Server equivalent of PostgreSQL 006_add_functional_search_indexes.sql
-- Create indexes for case-insensitive name search
-- SQL Server default collation is case-insensitive, so regular indexes suffice
-- (PostgreSQL required LOWER() functional indexes with varchar_pattern_ops)

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_last_name_lower' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_last_name_lower ON dbo.contacts(last_name);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contacts_first_name_lower' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_first_name_lower ON dbo.contacts(first_name);
