-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- ============================================================================
-- SQL Server Bulk Data Loader for Test Environment
-- ============================================================================
-- Loads test data from CSV files into the contact service schema.
-- Uses BULK INSERT for high-performance loading.
--
-- Prerequisites:
--   1. Schema initialized (all DDL scripts 001-016 applied)
--   2. CSV files present in test-data/ directory (accessible from SQL Server)
--   3. BULK INSERT requires the files to be accessible from the SQL Server host
--
-- Usage:
--   cd test-data
--   sqlcmd -S <host> -U <user> -P <password> -d <database> -i sql/load_sqlserver.sql
--
-- Note: Set the @csv_path variable below to the directory containing the CSV files
-- ============================================================================

SET NOCOUNT ON;
DECLARE @start_time DATETIME2 = SYSDATETIME();

PRINT '=== SQL Server Bulk Data Loader ===';
PRINT '';

-- ============================================================================
-- STEP 1: Create staging tables
-- ============================================================================
PRINT '=== Step 1: Creating staging tables ===';

IF OBJECT_ID('dbo.staging_contacts', 'U') IS NOT NULL DROP TABLE dbo.staging_contacts;
IF OBJECT_ID('dbo.staging_addresses', 'U') IS NOT NULL DROP TABLE dbo.staging_addresses;
IF OBJECT_ID('dbo.staging_contracts', 'U') IS NOT NULL DROP TABLE dbo.staging_contracts;

CREATE TABLE dbo.staging_contracts (
    company_id    UNIQUEIDENTIFIER,
    contract_id   UNIQUEIDENTIFIER,
    company_name  NVARCHAR(500),
    contract_name NVARCHAR(500)
);

CREATE TABLE dbo.staging_addresses (
    address_id     UNIQUEIDENTIFIER,
    street_number  NVARCHAR(50),
    street_name    NVARCHAR(255),
    street_type    NVARCHAR(50),
    city           NVARCHAR(255),
    state          NVARCHAR(50),
    zip_code       NVARCHAR(10),
    longitude      NVARCHAR(50),
    latitude       NVARCHAR(50)
);

CREATE TABLE dbo.staging_contacts (
    contact_id  UNIQUEIDENTIFIER,
    first_name  NVARCHAR(255),
    last_name   NVARCHAR(255),
    address_id  UNIQUEIDENTIFIER,
    email       NVARCHAR(255),
    phone       NVARCHAR(50),
    contract_id UNIQUEIDENTIFIER
);

-- ============================================================================
-- STEP 2: Load CSV data into staging tables
-- ============================================================================
PRINT '=== Step 2: Loading CSV data into staging tables ===';

BULK INSERT dbo.staging_contracts
FROM 'companies_and_contracts.csv'
WITH (
    FORMAT = 'CSV',
    FIRSTROW = 2,
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n',
    TABLOCK
);
PRINT CONCAT('Staged contracts: ', (SELECT COUNT(*) FROM dbo.staging_contracts));

BULK INSERT dbo.staging_addresses
FROM 'addresses.csv'
WITH (
    FORMAT = 'CSV',
    FIRSTROW = 2,
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n',
    TABLOCK
);
PRINT CONCAT('Staged addresses: ', (SELECT COUNT(*) FROM dbo.staging_addresses));

BULK INSERT dbo.staging_contacts
FROM 'contacts.csv'
WITH (
    FORMAT = 'CSV',
    FIRSTROW = 2,
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n',
    TABLOCK
);
PRINT CONCAT('Staged contacts: ', (SELECT COUNT(*) FROM dbo.staging_contacts));

-- ============================================================================
-- STEP 3: Load contracts (UUID PKs from CSV)
-- ============================================================================
PRINT '=== Step 3: Loading contracts ===';

INSERT INTO dbo.contracts (id, contract_name, company_id, company_name)
SELECT DISTINCT
    sc.contract_id,
    sc.contract_name,
    sc.company_id,
    sc.company_name
FROM dbo.staging_contracts sc
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.contracts c WHERE c.id = sc.contract_id
);

PRINT CONCAT('Contracts loaded: ', (SELECT COUNT(*) FROM dbo.contracts));

-- ============================================================================
-- STEP 4: Load standardized addresses with temp CSV ID column
-- ============================================================================
PRINT '=== Step 4: Loading standardized addresses ===';

-- Add temp column if not present
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.addresses') AND name = '_csv_id')
    ALTER TABLE dbo.addresses ADD _csv_id UNIQUEIDENTIFIER;

INSERT INTO dbo.addresses (locality, administrative_area, postal_code, country_code, _csv_id)
SELECT
    sa.city,
    sa.state,
    sa.zip_code,
    'US',
    sa.address_id
FROM dbo.staging_addresses sa;

-- Create index on _csv_id for join performance
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_std_addr_csv_id' AND object_id = OBJECT_ID('dbo.addresses'))
    CREATE INDEX idx_std_addr_csv_id ON dbo.addresses(_csv_id);

PRINT CONCAT('Standardized addresses loaded: ', (SELECT COUNT(*) FROM dbo.addresses WHERE _csv_id IS NOT NULL));

-- Insert address lines (street address as line 1)
INSERT INTO dbo.address_lines (address_id, line_order, line_value)
SELECT
    a.id,
    1,
    CONCAT(sa.street_number, ' ', sa.street_name, ' ', sa.street_type)
FROM dbo.staging_addresses sa
JOIN dbo.addresses a ON a._csv_id = sa.address_id;

PRINT CONCAT('Address lines loaded: ', (SELECT COUNT(*) FROM dbo.address_lines));

-- ============================================================================
-- STEP 5: Load contacts with temp CSV ID column
-- ============================================================================
PRINT '=== Step 5: Loading contacts ===';

-- Add temp column if not present
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contacts') AND name = '_csv_id')
    ALTER TABLE dbo.contacts ADD _csv_id UNIQUEIDENTIFIER;

SET IDENTITY_INSERT dbo.contacts OFF;

INSERT INTO dbo.contacts (first_name, last_name, email, _csv_id, last_name_lower, first_name_lower)
SELECT
    sc.first_name,
    sc.last_name,
    sc.email,
    sc.contact_id,
    LOWER(sc.last_name),
    LOWER(sc.first_name)
FROM dbo.staging_contacts sc;

-- Create index on _csv_id for join performance
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_contacts_csv_id' AND object_id = OBJECT_ID('dbo.contacts'))
    CREATE INDEX idx_contacts_csv_id ON dbo.contacts(_csv_id);

PRINT CONCAT('Contacts loaded: ', (SELECT COUNT(*) FROM dbo.contacts WHERE _csv_id IS NOT NULL));

-- ============================================================================
-- STEP 6: Link contacts to addresses (contact_addresses)
-- ============================================================================
PRINT '=== Step 6: Linking contacts to addresses ===';

INSERT INTO dbo.contact_addresses (contact_id, address_id, address_type, preferred)
SELECT
    c.id,
    a.id,
    'HOME',
    1
FROM dbo.staging_contacts sc
JOIN dbo.contacts c ON c._csv_id = sc.contact_id
JOIN dbo.addresses a ON a._csv_id = sc.address_id
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.contact_addresses ca
    WHERE ca.contact_id = c.id AND ca.address_type = 'HOME'
);

PRINT CONCAT('Contact addresses linked: ', (SELECT COUNT(*) FROM dbo.contact_addresses));

-- ============================================================================
-- STEP 7: Load contact emails
-- ============================================================================
PRINT '=== Step 7: Loading contact emails ===';

INSERT INTO dbo.contact_emails (contact_id, email, email_type)
SELECT
    c.id,
    sc.email,
    'HOME'
FROM dbo.staging_contacts sc
JOIN dbo.contacts c ON c._csv_id = sc.contact_id
WHERE sc.email IS NOT NULL AND sc.email != ''
AND NOT EXISTS (
    SELECT 1 FROM dbo.contact_emails ce
    WHERE ce.contact_id = c.id AND ce.email_type = 'HOME'
);

PRINT CONCAT('Contact emails loaded: ', (SELECT COUNT(*) FROM dbo.contact_emails));

-- ============================================================================
-- STEP 8: Load contact phones
-- ============================================================================
PRINT '=== Step 8: Loading contact phones ===';

INSERT INTO dbo.contact_phones (contact_id, phone_number, phone_type)
SELECT
    c.id,
    sc.phone,
    'HOME'
FROM dbo.staging_contacts sc
JOIN dbo.contacts c ON c._csv_id = sc.contact_id
WHERE sc.phone IS NOT NULL AND sc.phone != ''
AND NOT EXISTS (
    SELECT 1 FROM dbo.contact_phones cp
    WHERE cp.contact_id = c.id AND cp.phone_type = 'HOME'
);

PRINT CONCAT('Contact phones loaded: ', (SELECT COUNT(*) FROM dbo.contact_phones));

-- ============================================================================
-- STEP 9: Link contacts to contracts (contact_contracts)
-- ============================================================================
PRINT '=== Step 9: Linking contacts to contracts ===';

INSERT INTO dbo.contact_contracts (contact_id, contract_id, effective_date, expiration_date)
SELECT
    c.id,
    sc.contract_id,
    DATEFROMPARTS(YEAR(GETDATE()), 1, 1),
    DATEFROMPARTS(YEAR(GETDATE()), 12, 31)
FROM dbo.staging_contacts sc
JOIN dbo.contacts c ON c._csv_id = sc.contact_id
WHERE sc.contract_id IS NOT NULL;

PRINT CONCAT('Contact contracts linked: ', (SELECT COUNT(*) FROM dbo.contact_contracts));

-- ============================================================================
-- STEP 10: Populate contact_lookup table
-- ============================================================================
PRINT '=== Step 10: Populating contact lookup ===';

INSERT INTO dbo.contact_lookup (contact_id, partition_number)
SELECT
    c.id,
    c.id % 16
FROM dbo.contacts c
WHERE c._csv_id IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM dbo.contact_lookup cl WHERE cl.contact_id = c.id
);

PRINT CONCAT('Contact lookup entries: ', (SELECT COUNT(*) FROM dbo.contact_lookup));

-- ============================================================================
-- STEP 11: Cleanup temp columns and staging tables
-- ============================================================================
PRINT '=== Step 11: Cleaning up ===';

-- Drop temp indexes
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_contacts_csv_id' AND object_id = OBJECT_ID('dbo.contacts'))
    DROP INDEX idx_contacts_csv_id ON dbo.contacts;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_std_addr_csv_id' AND object_id = OBJECT_ID('dbo.addresses'))
    DROP INDEX idx_std_addr_csv_id ON dbo.addresses;

-- Drop temp columns
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.contacts') AND name = '_csv_id')
    ALTER TABLE dbo.contacts DROP COLUMN _csv_id;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.addresses') AND name = '_csv_id')
    ALTER TABLE dbo.addresses DROP COLUMN _csv_id;

-- Drop staging tables
IF OBJECT_ID('dbo.staging_contacts', 'U') IS NOT NULL DROP TABLE dbo.staging_contacts;
IF OBJECT_ID('dbo.staging_addresses', 'U') IS NOT NULL DROP TABLE dbo.staging_addresses;
IF OBJECT_ID('dbo.staging_contracts', 'U') IS NOT NULL DROP TABLE dbo.staging_contracts;

-- ============================================================================
-- STEP 12: Final statistics
-- ============================================================================
PRINT '';
PRINT '=== IMPORT COMPLETE ===';
PRINT CONCAT('Total contracts:          ', (SELECT COUNT(*) FROM dbo.contracts));
PRINT CONCAT('Total contacts:           ', (SELECT COUNT(*) FROM dbo.contacts));
PRINT CONCAT('Total addresses:          ', (SELECT COUNT(*) FROM dbo.addresses));
PRINT CONCAT('Total contact_addresses:  ', (SELECT COUNT(*) FROM dbo.contact_addresses));
PRINT CONCAT('Total contact_emails:     ', (SELECT COUNT(*) FROM dbo.contact_emails));
PRINT CONCAT('Total contact_phones:     ', (SELECT COUNT(*) FROM dbo.contact_phones));
PRINT CONCAT('Total contact_contracts:  ', (SELECT COUNT(*) FROM dbo.contact_contracts));
PRINT CONCAT('Total address_lines:      ', (SELECT COUNT(*) FROM dbo.address_lines));
PRINT CONCAT('Total contact_lookup:     ', (SELECT COUNT(*) FROM dbo.contact_lookup));
PRINT CONCAT('Elapsed time: ', DATEDIFF(SECOND, @start_time, SYSDATETIME()), ' seconds');

GO
