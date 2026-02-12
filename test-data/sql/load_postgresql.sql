-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- ============================================================================
-- PostgreSQL Bulk Data Loader for Test Environment
-- ============================================================================
-- Loads test data from CSV files into the contact service schema.
-- Uses \copy (client-side) so no superuser privileges are required.
--
-- Prerequisites:
--   1. Schema initialized (all DDL scripts 001-016 applied)
--   2. CSV files present in test-data/ directory
--
-- Usage:
--   cd test-data
--   psql -h <host> -U <user> -d <database> -f sql/load_postgresql.sql
-- ============================================================================

\timing on

BEGIN;

-- ============================================================================
-- STEP 1: Create staging tables
-- ============================================================================
SELECT '=== Step 1: Creating staging tables ===' AS status;

DROP TABLE IF EXISTS staging_contacts CASCADE;
DROP TABLE IF EXISTS staging_addresses CASCADE;
DROP TABLE IF EXISTS staging_contracts CASCADE;

CREATE TABLE staging_contracts (
    company_id   UUID,
    contract_id  UUID,
    company_name TEXT,
    contract_name TEXT
);

CREATE TABLE staging_addresses (
    address_id     UUID,
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
    contact_id  UUID,
    first_name  TEXT,
    last_name   TEXT,
    address_id  UUID,
    email       TEXT,
    phone       TEXT,
    contract_id UUID
);

-- ============================================================================
-- STEP 2: Load CSV data into staging tables
-- ============================================================================
SELECT '=== Step 2: Loading CSV data into staging tables ===' AS status;

\copy staging_contracts FROM 'companies_and_contracts.csv' WITH (FORMAT CSV, HEADER TRUE);
SELECT 'Staged contracts: ' || COUNT(*) FROM staging_contracts;

\copy staging_addresses FROM 'addresses.csv' WITH (FORMAT CSV, HEADER TRUE);
SELECT 'Staged addresses: ' || COUNT(*) FROM staging_addresses;

\copy staging_contacts FROM 'contacts.csv' WITH (FORMAT CSV, HEADER TRUE);
SELECT 'Staged contacts: ' || COUNT(*) FROM staging_contacts;

-- ============================================================================
-- STEP 3: Load contracts (UUID PKs from CSV)
-- ============================================================================
SELECT '=== Step 3: Loading contracts ===' AS status;

INSERT INTO contracts (id, contract_name, company_id, company_name)
SELECT DISTINCT
    sc.contract_id,
    sc.contract_name,
    sc.company_id,
    sc.company_name
FROM staging_contracts sc
ON CONFLICT DO NOTHING;

SELECT 'Contracts loaded: ' || COUNT(*) FROM contracts;

-- ============================================================================
-- STEP 4: Load standardized addresses with temp CSV ID column
-- ============================================================================
SELECT '=== Step 4: Loading standardized addresses ===' AS status;

-- Add temp column to track CSV address_id for later joins
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS _csv_id UUID;

INSERT INTO addresses (locality, administrative_area, postal_code, country_code, _csv_id)
SELECT
    sa.city,
    sa.state,
    sa.zip_code,
    'US',
    sa.address_id
FROM staging_addresses sa
ON CONFLICT DO NOTHING;

-- Create index on _csv_id for join performance
CREATE INDEX IF NOT EXISTS idx_std_addr_csv_id ON addresses(_csv_id);

SELECT 'Standardized addresses loaded: ' || COUNT(*) FROM addresses WHERE _csv_id IS NOT NULL;

-- Insert address lines (street address as line 1)
INSERT INTO address_lines (address_id, line_order, line_value)
SELECT
    a.id,
    1,
    sa.street_number || ' ' || sa.street_name || ' ' || sa.street_type
FROM staging_addresses sa
JOIN addresses a ON a._csv_id = sa.address_id;

SELECT 'Address lines loaded: ' || COUNT(*) FROM address_lines;

-- ============================================================================
-- STEP 5: Load contacts with temp CSV ID column
-- ============================================================================
SELECT '=== Step 5: Loading contacts ===' AS status;

-- Add temp column to track CSV contact_id for later joins
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS _csv_id UUID;

INSERT INTO contacts (first_name, last_name, email, _csv_id)
SELECT
    sc.first_name,
    sc.last_name,
    sc.email,
    sc.contact_id
FROM staging_contacts sc;

-- The trigger should set last_name_lower/first_name_lower automatically,
-- but update any that got missed
UPDATE contacts SET
    last_name_lower = LOWER(last_name),
    first_name_lower = LOWER(first_name)
WHERE _csv_id IS NOT NULL
  AND (last_name_lower IS NULL OR first_name_lower IS NULL);

-- Create index on _csv_id for join performance
CREATE INDEX IF NOT EXISTS idx_contacts_csv_id ON contacts(_csv_id);

SELECT 'Contacts loaded: ' || COUNT(*) FROM contacts WHERE _csv_id IS NOT NULL;

-- ============================================================================
-- STEP 6: Link contacts to addresses (contact_addresses)
-- ============================================================================
SELECT '=== Step 6: Linking contacts to addresses ===' AS status;

INSERT INTO contact_addresses (contact_id, address_id, address_type, preferred)
SELECT
    c.id,
    a.id,
    'HOME',
    true
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
JOIN addresses a ON a._csv_id = sc.address_id
ON CONFLICT (contact_id, address_type) DO NOTHING;

SELECT 'Contact addresses linked: ' || COUNT(*) FROM contact_addresses;

-- ============================================================================
-- STEP 7: Load contact emails
-- ============================================================================
SELECT '=== Step 7: Loading contact emails ===' AS status;

INSERT INTO contact_emails (contact_id, email, email_type)
SELECT
    c.id,
    sc.email,
    'HOME'
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.email IS NOT NULL AND sc.email != ''
ON CONFLICT (contact_id, email_type) DO NOTHING;

SELECT 'Contact emails loaded: ' || COUNT(*) FROM contact_emails;

-- ============================================================================
-- STEP 8: Load contact phones
-- ============================================================================
SELECT '=== Step 8: Loading contact phones ===' AS status;

INSERT INTO contact_phones (contact_id, phone_number, phone_type)
SELECT
    c.id,
    sc.phone,
    'HOME'
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.phone IS NOT NULL AND sc.phone != ''
ON CONFLICT (contact_id, phone_type) DO NOTHING;

SELECT 'Contact phones loaded: ' || COUNT(*) FROM contact_phones;

-- ============================================================================
-- STEP 9: Link contacts to contracts (contact_contracts)
-- ============================================================================
SELECT '=== Step 9: Linking contacts to contracts ===' AS status;

INSERT INTO contact_contracts (contact_id, contract_id, effective_date, expiration_date)
SELECT
    c.id,
    sc.contract_id,
    DATE_TRUNC('year', CURRENT_DATE)::timestamptz,
    (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year' - INTERVAL '1 day')::timestamptz
FROM staging_contacts sc
JOIN contacts c ON c._csv_id = sc.contact_id
WHERE sc.contract_id IS NOT NULL;

SELECT 'Contact contracts linked: ' || COUNT(*) FROM contact_contracts;

-- ============================================================================
-- STEP 10: Populate contact_lookup table
-- ============================================================================
SELECT '=== Step 10: Populating contact lookup ===' AS status;

INSERT INTO contact_lookup (contact_id, partition_number)
SELECT
    c.id,
    (c.id % 16)::integer
FROM contacts c
WHERE c._csv_id IS NOT NULL
ON CONFLICT (contact_id) DO NOTHING;

SELECT 'Contact lookup entries: ' || COUNT(*) FROM contact_lookup;

-- ============================================================================
-- STEP 11: Cleanup temp columns and staging tables
-- ============================================================================
SELECT '=== Step 11: Cleaning up ===' AS status;

-- Drop temp indexes first
DROP INDEX IF EXISTS idx_contacts_csv_id;
DROP INDEX IF EXISTS idx_std_addr_csv_id;

-- Drop temp columns
ALTER TABLE contacts DROP COLUMN IF EXISTS _csv_id;
ALTER TABLE addresses DROP COLUMN IF EXISTS _csv_id;

-- Drop staging tables
DROP TABLE IF EXISTS staging_contacts CASCADE;
DROP TABLE IF EXISTS staging_addresses CASCADE;
DROP TABLE IF EXISTS staging_contracts CASCADE;

-- ============================================================================
-- STEP 12: Final statistics
-- ============================================================================
SELECT '=== IMPORT COMPLETE ===' AS status;
SELECT 'Total contracts:          ' || COUNT(*) FROM contracts;
SELECT 'Total contacts:           ' || COUNT(*) FROM contacts;
SELECT 'Total addresses:          ' || COUNT(*) FROM addresses;
SELECT 'Total contact_addresses:  ' || COUNT(*) FROM contact_addresses;
SELECT 'Total contact_emails:     ' || COUNT(*) FROM contact_emails;
SELECT 'Total contact_phones:     ' || COUNT(*) FROM contact_phones;
SELECT 'Total contact_contracts:  ' || COUNT(*) FROM contact_contracts;
SELECT 'Total address_lines:      ' || COUNT(*) FROM address_lines;
SELECT 'Total contact_lookup:     ' || COUNT(*) FROM contact_lookup;

COMMIT;
