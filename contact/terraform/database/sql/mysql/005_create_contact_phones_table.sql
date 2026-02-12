-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_phones table (MySQL equivalent of PostgreSQL migration 005)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_005;

DELIMITER //
CREATE PROCEDURE run_migration_005()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_phones (
        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id   BIGINT NOT NULL,
        phone_number VARCHAR(50) NOT NULL,
        phone_type   VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_phone_type UNIQUE (contact_id, phone_type),
        CONSTRAINT fk_contact_phones_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
    ) COMMENT = 'Stores contact phone numbers with type (HOME, BUSINESS, MAILING)';

    -- Create index for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_phones' AND index_name = 'idx_contact_phones_contact_id') THEN
        CREATE INDEX idx_contact_phones_contact_id ON contact_phones(contact_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_005();
DROP PROCEDURE IF EXISTS run_migration_005;
