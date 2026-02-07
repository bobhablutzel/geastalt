-- SQL Server equivalent of PostgreSQL 013_create_plans_table.sql
-- Create plans table for insurance/benefit plan definitions
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'plans' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.plans (
        id           BIGINT IDENTITY(1,1) PRIMARY KEY,
        plan_name    VARCHAR(255) NOT NULL,
        carrier_id   INTEGER NOT NULL,
        carrier_name VARCHAR(255) NOT NULL,
        created_at   DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        updated_at   DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
    );
END;

-- Create index for carrier lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_plans_carrier_id' AND object_id = OBJECT_ID('dbo.plans'))
    CREATE INDEX idx_plans_carrier_id ON dbo.plans(carrier_id);
