-- Migration 010: Add index for phone number searches (MySQL equivalent of PostgreSQL migration 010)
-- This enables efficient lookups of contacts by phone number
-- Note: MySQL does not support CONCURRENTLY; use ALGORITHM=INPLACE for online DDL

DROP PROCEDURE IF EXISTS run_migration_010;

DELIMITER //
CREATE PROCEDURE run_migration_010()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_phones' AND index_name = 'idx_contact_phones_phone_number') THEN
        CREATE INDEX idx_contact_phones_phone_number ON contact_phones (phone_number);
    END IF;
END //
DELIMITER ;

CALL run_migration_010();
DROP PROCEDURE IF EXISTS run_migration_010;
