-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- ============================================================================
-- MySQL Bulk Data Loader for Test Environment
-- ============================================================================
-- Loads test data from CSV files into the contact service schema.
-- Uses LOAD DATA LOCAL INFILE for bulk loading.
--
-- Prerequisites:
--   1. Schema initialized (all DDL scripts 001-016 applied)
--   2. CSV files present in test-data/ directory
--   3. MySQL client started with --local-infile=1
--
-- Usage:
--   cd test-data
--   mysql --local-infile=1 -h <host> -u <user> -p <database> < sql/load_mysql.sql
-- ============================================================================

SET @start_time = NOW();

-- ============================================================================
-- STEP 1: Create staging tables
-- ============================================================================
SELECT '=== Step 1: Creating staging tables ===' AS status;

DROP TABLE IF EXISTS staging_contacts;
DROP TABLE IF EXISTS staging_addresses;
DROP TABLE IF EXISTS staging_contracts;

CREATE TABLE staging_contracts (
    company_id   VARCHAR(36),
    contract_id  VARCHAR(36),
    company_name TEXT,
    contract_name TEXT
);

CREATE TABLE staging_addresses (
    address_id     VARCHAR(36),
    street_number  TEXT,
    street_name    TEXT,
    street_type    TEXT,
    city           TEXT,
    state          TEXT,
    zip_code       TEXT,
    longitude      TEXT,
    latitude       TEXT
);

CREATE TABLE staging_contacts (
    contact_id  VARCHAR(36),
    first_name  TEXT,
    last_name   TEXT,
    address_id  VARCHAR(36),
    email       TEXT,
    phone       TEXT,
    contract_id VARCHAR(36)
);

-- ============================================================================
-- STEP 2: Load CSV data into staging tables
-- ============================================================================
SELECT '=== Step 2: Loading CSV data into staging tables ===' AS status;

LOAD DATA LOCAL INFILE 'companies_and_contracts.csv'
INTO TABLE staging_contracts
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES;
SELECT CONCAT('Staged contracts: ', COUNT(*)) AS status FROM staging_contracts;

LOAD DATA LOCAL INFILE 'addresses.csv'
INTO TABLE staging_addresses
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES;
SELECT CONCAT('Staged addresses: ', COUNT(*)) AS status FROM staging_addresses;

LOAD DATA LOCAL INFILE 'contacts.csv'
INTO TABLE staging_contacts
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES;
SELECT CONCAT('Staged contacts: ', COUNT(*)) AS status FROM staging_contacts;

-- ============================================================================
-- STEP 3: Load contracts (UUID PKs from CSV)
-- ============================================================================
SELECT '=== Step 3: Loading contracts ===' AS status;

INSERT IGNORE INTO contracts (id, contract_name, company_id, company_name)
SELECT DISTINCT
    sc.contract_id,
    sc.contract_name,
    sc.company_id,
    sc.company_name
FROM staging_contracts sc;

SELECT CONCAT('Contracts loaded: ', COUNT(*)) AS status FROM contracts;

-- ============================================================================
-- STEP 4: Load standardized addresses with temp CSV ID column
-- ============================================================================
SELECT '=== Step 4: Loading standardized addresses ===' AS status;

-- Add temp column if not present
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'addresses' AND column_name = '_csv_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE addresses ADD COLUMN _csv_id VARCHAR(36)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO addresses (locality, administrative_area, postal_code, country_code, _csv_id)
SELECT
    sa.city,
    sa.state,
    sa.zip_code,
    'US',
    sa.address_id
FROM staging_addresses sa
ON DUPLICATE KEY UPDATE _csv_id = VALUES(_csv_id);

-- Create index on _csv_id for join performance
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'addresses' AND index_name = 'idx_std_addr_csv_id');
SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_std_addr_csv_id ON addresses(_csv_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT CONCAT('Standardized addresses loaded: ', COUNT(*)) AS status FROM addresses WHERE _csv_id IS NOT NULL;

-- Insert address lines (street address as line 1)
INSERT INTO address_lines (address_id, line_order, line_value)
SELECT
    a.id,
    1,
    CONCAT(sa.street_number, ' ', sa.street_name, ' ', sa.street_type)
FROM staging_addresses sa
JOIN addresses a ON a._csv_id = sa.address_id;

SELECT CONCAT('Address lines loaded: ', COUNT(*)) AS status FROM address_lines;

-- ============================================================================
-- STEP 5: Load contacts with temp CSV ID column
-- ============================================================================
SELECT '=== Step 5: Loading contacts ===' AS status;

-- Add temp column if not present
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = '_csv_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE contacts ADD COLUMN _csv_id VARCHAR(36)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO contacts (first_name, last_name, email, _csv_id, last_name_lower, first_name_lower)
SELECT
    sc.first_name,
    sc.last_name,
    sc.email,
    sc.contact_id,
    LOWER(sc.last_name),
    LOWER(sc.first_name)
FROM staging_contacts sc;

-- Create index on _csv_id for join performance
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_csv_id');
SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_contacts_csv_id ON contacts(_csv_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT CONCAT('Contacts loaded: ', COUNT(*)) AS status FROM contacts WHERE _csv_id IS NOT NULL;

-- ============================================================================
-- STEP 6: Link contacts to addresses (contact_addresses)
-- ============================================================================
SELECT '=== Step 6: Linking contacts to addresses ===' AS status;

INSERT IGNORE INTO contact_addresses (contact_id, address_id, address_type, preferred)
SELECT
    c.id,
    a.id,
    'HOME',
    1
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
JOIN addresses a ON a._csv_id = sc.address_id;

SELECT CONCAT('Contact addresses linked: ', COUNT(*)) AS status FROM contact_addresses;

-- ============================================================================
-- STEP 7: Load contact emails
-- ============================================================================
SELECT '=== Step 7: Loading contact emails ===' AS status;

INSERT IGNORE INTO contact_emails (contact_id, email, email_type)
SELECT
    c.id,
    sc.email,
    'HOME'
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.email IS NOT NULL AND sc.email != '';

SELECT CONCAT('Contact emails loaded: ', COUNT(*)) AS status FROM contact_emails;

-- ============================================================================
-- STEP 8: Load contact phones
-- ============================================================================
SELECT '=== Step 8: Loading contact phones ===' AS status;

INSERT IGNORE INTO contact_phones (contact_id, phone_number, phone_type)
SELECT
    c.id,
    sc.phone,
    'HOME'
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.phone IS NOT NULL AND sc.phone != '';

SELECT CONCAT('Contact phones loaded: ', COUNT(*)) AS status FROM contact_phones;

-- ============================================================================
-- STEP 9: Link contacts to contracts (contact_contracts)
-- ============================================================================
SELECT '=== Step 9: Linking contacts to contracts ===' AS status;

INSERT INTO contact_contracts (contact_id, contract_id, effective_date, expiration_date)
SELECT
    c.id,
    sc.contract_id,
    CONCAT(YEAR(CURRENT_DATE), '-01-01 00:00:00'),
    CONCAT(YEAR(CURRENT_DATE), '-12-31 23:59:59')
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.contract_id IS NOT NULL;

SELECT CONCAT('Contact contracts linked: ', COUNT(*)) AS status FROM contact_contracts;

-- ============================================================================
-- STEP 10: Populate contact_lookup table
-- ============================================================================
SELECT '=== Step 10: Populating contact lookup ===' AS status;

INSERT IGNORE INTO contact_lookup (contact_id, partition_number)
SELECT
    c.id,
    c.id MOD 16
FROM contacts c
WHERE c._csv_id IS NOT NULL;

SELECT CONCAT('Contact lookup entries: ', COUNT(*)) AS status FROM contact_lookup;

-- ============================================================================
-- STEP 11: Cleanup temp columns and staging tables
-- ============================================================================
SELECT '=== Step 11: Cleaning up ===' AS status;

-- Drop temp indexes
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_csv_id');
SET @sql = IF(@idx_exists > 0,
    'DROP INDEX idx_contacts_csv_id ON contacts',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'addresses' AND index_name = 'idx_std_addr_csv_id');
SET @sql = IF(@idx_exists > 0,
    'DROP INDEX idx_std_addr_csv_id ON addresses',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop temp columns
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = '_csv_id');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE contacts DROP COLUMN _csv_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'addresses' AND column_name = '_csv_id');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE addresses DROP COLUMN _csv_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop staging tables
DROP TABLE IF EXISTS staging_contacts;
DROP TABLE IF EXISTS staging_addresses;
DROP TABLE IF EXISTS staging_contracts;

-- ============================================================================
-- STEP 12: Final statistics
-- ============================================================================
SELECT '=== IMPORT COMPLETE ===' AS status;
SELECT CONCAT('Total contracts:          ', COUNT(*)) AS status FROM contracts;
SELECT CONCAT('Total contacts:           ', COUNT(*)) AS status FROM contacts;
SELECT CONCAT('Total addresses:          ', COUNT(*)) AS status FROM addresses;
SELECT CONCAT('Total contact_addresses:  ', COUNT(*)) AS status FROM contact_addresses;
SELECT CONCAT('Total contact_emails:     ', COUNT(*)) AS status FROM contact_emails;
SELECT CONCAT('Total contact_phones:     ', COUNT(*)) AS status FROM contact_phones;
SELECT CONCAT('Total contact_contracts:  ', COUNT(*)) AS status FROM contact_contracts;
SELECT CONCAT('Total address_lines:      ', COUNT(*)) AS status FROM address_lines;
SELECT CONCAT('Total contact_lookup:     ', COUNT(*)) AS status FROM contact_lookup;
SELECT CONCAT('Elapsed time: ', TIMEDIFF(NOW(), @start_time)) AS status;
