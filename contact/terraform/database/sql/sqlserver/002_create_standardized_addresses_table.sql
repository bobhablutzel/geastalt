-- SQL Server equivalent of PostgreSQL 002_create_standardized_addresses_table.sql
-- Create standardized_addresses table
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'standardized_addresses' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.standardized_addresses (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        street_address    VARCHAR(255),
        secondary_address VARCHAR(255),
        city              VARCHAR(255),
        state             VARCHAR(50),
        zip_code          VARCHAR(10),
        zip_plus4         VARCHAR(10),
        -- Persisted computed columns for unique index (handles NULLs like COALESCE in PostgreSQL)
        street_address_key    AS ISNULL(street_address, '')    PERSISTED,
        secondary_address_key AS ISNULL(secondary_address, '') PERSISTED,
        city_key              AS ISNULL(city, '')              PERSISTED,
        state_key             AS ISNULL(state, '')             PERSISTED,
        zip_code_key          AS ISNULL(zip_code, '')          PERSISTED,
        zip_plus4_key         AS ISNULL(zip_plus4, '')         PERSISTED
    );
END;

-- Create unique index on computed columns to prevent duplicates
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_standardized_addresses_unique' AND object_id = OBJECT_ID('dbo.standardized_addresses'))
    CREATE UNIQUE INDEX idx_standardized_addresses_unique
    ON dbo.standardized_addresses(
        street_address_key,
        secondary_address_key,
        city_key,
        state_key,
        zip_code_key,
        zip_plus4_key
    );
