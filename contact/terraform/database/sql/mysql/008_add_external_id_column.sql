-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Add external_id column to contacts table for non-sequential contact identifiers (MySQL equivalent of PostgreSQL migration 008)
-- External IDs have format "NH" + 15 digits (e.g., "NH847293615028374")

DROP PROCEDURE IF EXISTS run_migration_008;

DELIMITER //
CREATE PROCEDURE run_migration_008()
BEGIN
    -- Add the external_id column if it doesn't already exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = 'external_id') THEN
        ALTER TABLE contacts ADD COLUMN external_id VARCHAR(17);
    END IF;

    -- Create unique index for external_id lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_external_id') THEN
        CREATE UNIQUE INDEX idx_contacts_external_id ON contacts (external_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_008();
DROP PROCEDURE IF EXISTS run_migration_008;
