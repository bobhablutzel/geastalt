-- SQL Server equivalent of PostgreSQL 014_create_contact_plans_table.sql
-- Create contact_plans table linking contacts to their plans
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_plans' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_plans (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id      BIGINT NOT NULL REFERENCES dbo.contacts(id),
        plan_id         BIGINT NOT NULL REFERENCES dbo.plans(id),
        effective_date  DATETIMEOFFSET NOT NULL,
        expiration_date DATETIMEOFFSET NOT NULL,
        created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        updated_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
    );
END;

-- Create indexes for faster lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_plans_contact_id' AND object_id = OBJECT_ID('dbo.contact_plans'))
    CREATE INDEX idx_contact_plans_contact_id ON dbo.contact_plans(contact_id);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_plans_plan_id' AND object_id = OBJECT_ID('dbo.contact_plans'))
    CREATE INDEX idx_contact_plans_plan_id ON dbo.contact_plans(plan_id);
