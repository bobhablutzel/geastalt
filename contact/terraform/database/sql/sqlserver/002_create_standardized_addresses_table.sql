-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- SQL Server: Create addresses table (international address model)
-- This script is idempotent and can be run multiple times safely

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'addresses' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.addresses (
        id                   BIGINT IDENTITY(1,1) PRIMARY KEY,
        locality             VARCHAR(255),
        administrative_area  VARCHAR(100),
        postal_code          VARCHAR(20),
        country_code         VARCHAR(2),
        sub_locality         VARCHAR(255),
        sorting_code         VARCHAR(20),
        validated            BIT NOT NULL DEFAULT 0,
        -- Persisted computed columns for index
        locality_key              AS ISNULL(locality, '')             PERSISTED,
        administrative_area_key   AS ISNULL(administrative_area, '')  PERSISTED,
        postal_code_key           AS ISNULL(postal_code, '')          PERSISTED,
        country_code_key          AS ISNULL(country_code, '')         PERSISTED
    );
END;

-- Create index for deduplication lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_addresses_lookup' AND object_id = OBJECT_ID('dbo.addresses'))
    CREATE INDEX idx_addresses_lookup
    ON dbo.addresses(
        locality_key,
        administrative_area_key,
        postal_code_key,
        country_code_key
    );

-- Create address_lines table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'address_lines' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.address_lines (
        id            BIGINT IDENTITY(1,1) PRIMARY KEY,
        address_id    BIGINT NOT NULL REFERENCES dbo.addresses(id) ON DELETE CASCADE,
        line_order    INT NOT NULL,
        line_value    VARCHAR(255) NOT NULL,
        CONSTRAINT uk_address_line_order UNIQUE (address_id, line_order)
    );
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_address_lines_address_id' AND object_id = OBJECT_ID('dbo.address_lines'))
    CREATE INDEX idx_address_lines_address_id ON dbo.address_lines(address_id);
