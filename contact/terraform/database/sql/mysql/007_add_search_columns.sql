-- Add lowercase search columns to contacts table (MySQL equivalent of PostgreSQL migration 007)
-- This avoids functional index overhead by storing pre-computed lowercase values

DROP PROCEDURE IF EXISTS run_migration_007;

DELIMITER //
CREATE PROCEDURE run_migration_007()
BEGIN
    -- Add the search columns if they don't already exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = 'last_name_lower') THEN
        ALTER TABLE contacts ADD COLUMN last_name_lower VARCHAR(255);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = 'first_name_lower') THEN
        ALTER TABLE contacts ADD COLUMN first_name_lower VARCHAR(255);
    END IF;

    -- Populate existing data (safe to run multiple times; overwrites with same values)
    UPDATE contacts SET
        last_name_lower = LOWER(last_name),
        first_name_lower = LOWER(first_name)
    WHERE last_name_lower IS NULL OR first_name_lower IS NULL;

    -- Create indexes for prefix searches
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_last_name_search') THEN
        CREATE INDEX idx_contacts_last_name_search ON contacts (last_name_lower);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_first_name_search') THEN
        CREATE INDEX idx_contacts_first_name_search ON contacts (first_name_lower);
    END IF;

    -- Drop the old functional indexes (no longer needed) if they exist
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_last_name_lower') THEN
        DROP INDEX idx_contacts_last_name_lower ON contacts;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_first_name_lower') THEN
        DROP INDEX idx_contacts_first_name_lower ON contacts;
    END IF;
END //
DELIMITER ;

CALL run_migration_007();
DROP PROCEDURE IF EXISTS run_migration_007;

-- Create triggers to keep search columns in sync
-- MySQL requires separate triggers for INSERT and UPDATE
-- Triggers cannot be created inside stored procedures, so they are handled here
-- DROP TRIGGER IF EXISTS is natively idempotent in MySQL

DROP TRIGGER IF EXISTS trg_sync_contact_search_insert;
DROP TRIGGER IF EXISTS trg_sync_contact_search_update;

DELIMITER //
CREATE TRIGGER trg_sync_contact_search_insert
    BEFORE INSERT ON contacts
    FOR EACH ROW
BEGIN
    SET NEW.last_name_lower = LOWER(NEW.last_name);
    SET NEW.first_name_lower = LOWER(NEW.first_name);
END //

CREATE TRIGGER trg_sync_contact_search_update
    BEFORE UPDATE ON contacts
    FOR EACH ROW
BEGIN
    SET NEW.last_name_lower = LOWER(NEW.last_name);
    SET NEW.first_name_lower = LOWER(NEW.first_name);
END //
DELIMITER ;
