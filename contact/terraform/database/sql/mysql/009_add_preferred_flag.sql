-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Add preferred flag to contact_addresses and contact_emails tables (MySQL equivalent of PostgreSQL migration 009)

DROP PROCEDURE IF EXISTS run_migration_009;

DELIMITER //
CREATE PROCEDURE run_migration_009()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contact_addresses' AND column_name = 'preferred') THEN
        ALTER TABLE contact_addresses ADD COLUMN preferred BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contact_emails' AND column_name = 'preferred') THEN
        ALTER TABLE contact_emails ADD COLUMN preferred BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END //
DELIMITER ;

CALL run_migration_009();
DROP PROCEDURE IF EXISTS run_migration_009;
